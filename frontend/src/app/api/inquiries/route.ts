import { NextResponse, type NextRequest } from "next/server";

import { serverEnv, isProduction } from "@/lib/env";
import { digestOf, hashClientIdentifier, logger, maskEmail, newRequestId } from "@/lib/logger";
import { getInquiryRateLimiter } from "@/lib/rate-limit";
import { inquirySchema, toInquiryPayload } from "@/lib/validation/inquiry";

/**
 * Server-side proxy for the inquiry intake API.
 *
 * Responsibilities, in order:
 *   1. reject oversized bodies before they are parsed (memory exhaustion protection);
 *   2. rate limit per hashed client IP – independent from the limit enforced by the API;
 *   3. swallow submissions that filled the honeypot field, without telling the bot;
 *   4. re-validate everything with the same Zod schema the browser used (client validation is UX,
 *      never a security control);
 *   5. forward the payload server-to-server with an idempotency key and a correlation id, mapping
 *      the upstream RFC 9457 problem document back to the browser unchanged;
 *   6. log structured, PII-free lines for every outcome.
 *
 * Keeping the API behind this boundary means the Spring service does not need to be reachable from
 * the public internet, which removes CORS from the browser path and shrinks the attack surface.
 */
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const revalidate = 0;

const MAX_BODY_BYTES = 16 * 1024;
const IDEMPOTENCY_KEY_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{7,63}$/;

export async function POST(request: NextRequest): Promise<NextResponse> {
  const requestId = newRequestId();
  const startedAt = Date.now();

  try {
    const declaredLength = Number.parseInt(request.headers.get("content-length") ?? "", 10);
    if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_BYTES) {
      return problem(requestId, 413, "payload-too-large", "Payload too large",
        "The submitted inquiry is larger than the accepted limit.");
    }

    const rawBody = await request.text();
    if (Buffer.byteLength(rawBody, "utf8") > MAX_BODY_BYTES) {
      return problem(requestId, 413, "payload-too-large", "Payload too large",
        "The submitted inquiry is larger than the accepted limit.");
    }

    const clientKey = hashClientIdentifier(clientIp(request));
    const decision = getInquiryRateLimiter({
      limit: serverEnv.RATE_LIMIT_PER_MINUTE,
      burstLimit: serverEnv.RATE_LIMIT_BURST
    }).check(clientKey);

    if (!decision.allowed) {
      logger.warn("inquiry.rate_limited", {
        requestId,
        clientFp: clientKey,
        retryAfterSeconds: decision.retryAfterSeconds
      });
      return problem(requestId, 429, "rate-limit-exceeded", "Too many requests",
        `Too many submissions from this client. Please wait ${decision.retryAfterSeconds} seconds.`,
        { "Retry-After": String(decision.retryAfterSeconds) });
    }

    let parsedBody: unknown;
    try {
      parsedBody = JSON.parse(rawBody) as unknown;
    } catch {
      return problem(requestId, 400, "malformed-request", "Malformed request",
        "The request body could not be parsed as JSON.");
    }

    const validation = inquirySchema.safeParse(parsedBody);
    if (!validation.success) {
      const errors = validation.error.issues.map((issue) => ({
        field: issue.path.join(".") || "_",
        message: issue.message
      }));
      logger.info("inquiry.rejected", { requestId, reason: "validation", errors: errors.map((e) => e.field) });
      return problem(requestId, 400, "validation-failed", "Validation failed",
        "One or more fields are invalid. See 'errors' for details.", {}, errors);
    }

    const honeypot = validation.data.companyWebsite;
    if (typeof honeypot === "string" && honeypot.trim().length > 0) {
      // A filled honeypot means a naive bot. Answer with a plausible success so the bot stops
      // retrying, but never forward or store the submission.
      logger.warn("inquiry.honeypot_triggered", { requestId, payloadDigest: digestOf(rawBody) });
      return NextResponse.json(
        { reference: "00000000-0000-4000-8000-000000000000", status: "RECEIVED", receivedAt: new Date().toISOString() },
        { status: 201, headers: responseHeaders(requestId) }
      );
    }

    const payload = toInquiryPayload(validation.data);
    const upstream = await fetch(`${serverEnv.API_BASE_URL}/api/inquiries`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Idempotency-Key": normaliseIdempotencyKey(request.headers.get("idempotency-key")),
        "X-Request-Id": requestId,
        "User-Agent": "nk-ethical-web/1.0"
      },
      body: JSON.stringify(payload),
      cache: "no-store",
      signal: AbortSignal.timeout(serverEnv.INQUIRY_FORWARD_TIMEOUT_MS)
    });

    const upstreamBody: unknown = await readJson(upstream);
    const durationMs = Date.now() - startedAt;

    if (upstream.ok) {
      logger.info("inquiry.accepted", {
        requestId,
        reference: stringField(upstreamBody, "reference"),
        email: maskEmail(payload.email),
        upstreamStatus: upstream.status,
        durationMs
      });
      return NextResponse.json(upstreamBody ?? {}, {
        status: 201,
        headers: responseHeaders(requestId)
      });
    }

    logger.warn("inquiry.upstream_rejected", {
      requestId,
      upstreamStatus: upstream.status,
      durationMs
    });
    return NextResponse.json(upstreamBody ?? genericProblem(requestId, 502), {
      status: upstream.status >= 400 && upstream.status < 600 ? upstream.status : 502,
      headers: responseHeaders(requestId, retryAfterOf(upstream.headers.get("retry-after")))
    });
  } catch (error) {
    const name = error instanceof Error ? error.name : "UnknownError";
    if (name === "TimeoutError" || name === "AbortError") {
      logger.error("inquiry.upstream_timeout", { requestId, durationMs: Date.now() - startedAt });
      return problem(requestId, 504, "upstream-timeout", "Request timed out",
        "The intake service did not respond in time. Please try again.");
    }
    logger.error("inquiry.failed", {
      requestId,
      durationMs: Date.now() - startedAt,
      error: error instanceof Error ? error.message : String(error)
    });
    return problem(requestId, 502, "upstream-unavailable", "Service unavailable",
      "We could not reach the intake service. Please try again in a moment.");
  }
}

/** Everything except POST is refused, which also documents the contract to crawlers. */
export function GET(): NextResponse {
  return problem(newRequestId(), 405, "method-not-allowed", "Method not allowed",
    "Submit inquiries with POST /api/inquiries.", { Allow: "POST" });
}

function clientIp(request: NextRequest): string {
  const forwardedFor = request.headers.get("x-forwarded-for");
  if (forwardedFor) {
    const first = forwardedFor.split(",")[0]?.trim();
    if (first) {
      return first;
    }
  }
  return request.headers.get("x-real-ip")?.trim() ?? "unknown";
}

function normaliseIdempotencyKey(candidate: string | null): string {
  if (candidate) {
    const trimmed = candidate.trim();
    if (IDEMPOTENCY_KEY_PATTERN.test(trimmed)) {
      return trimmed;
    }
  }
  return crypto.randomUUID();
}

function responseHeaders(requestId: string, retryAfter?: string): Record<string, string> {
  const headers: Record<string, string> = {
    "Cache-Control": "no-store, no-cache, must-revalidate",
    "X-Request-Id": requestId,
    "X-Content-Type-Options": "nosniff"
  };
  if (retryAfter) {
    headers["Retry-After"] = retryAfter;
  }
  return headers;
}

function retryAfterOf(header: string | null): string | undefined {
  return header && /^\d+$/.test(header) ? header : undefined;
}

function problem(
  requestId: string,
  status: number,
  code: string,
  title: string,
  detail: string,
  extraHeaders: Record<string, string> = {},
  errors?: Array<{ field: string; message: string }>
): NextResponse {
  return NextResponse.json(
    genericProblem(requestId, status, code, title, detail, errors),
    { status, headers: { ...responseHeaders(requestId), ...extraHeaders } }
  );
}

function genericProblem(
  requestId: string,
  status: number,
  code = "internal-error",
  title = "Internal server error",
  detail = "Something went wrong while processing your inquiry.",
  errors?: Array<{ field: string; message: string }>
): Record<string, unknown> {
  const body: Record<string, unknown> = {
    type: `https://errors.nkethical.example/${code}`,
    title,
    status,
    detail,
    code,
    requestId,
    ...(isProduction ? {} : { environment: serverEnv.NODE_ENV })
  };
  if (errors && errors.length > 0) {
    body.errors = errors;
  }
  return body;
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return (await response.json()) as unknown;
  } catch {
    return null;
  }
}

function stringField(value: unknown, key: string): string | undefined {
  if (typeof value === "object" && value !== null && !Array.isArray(value)) {
    const field = (value as Record<string, unknown>)[key];
    return typeof field === "string" ? field : undefined;
  }
  return undefined;
}
