import type { InquiryPayload } from "@/lib/validation/inquiry";

/** Successful submission as returned by the API (and by the proxy route handler). */
export interface SubmissionResult {
  reference: string;
  status: string;
  receivedAt: string;
}

/** A single field level problem, as reported by the server. */
export interface FieldIssue {
  field: string;
  message: string;
}

export class InquiryApiError extends Error {
  readonly status: number;
  readonly issues: FieldIssue[];
  readonly requestId?: string;
  readonly retryAfterSeconds?: number;

  constructor(
    message: string,
    options: {
      status: number;
      issues?: FieldIssue[];
      requestId?: string;
      retryAfterSeconds?: number;
    }
  ) {
    super(message);
    this.name = "InquiryApiError";
    this.status = options.status;
    this.issues = options.issues ?? [];
    this.requestId = options.requestId;
    this.retryAfterSeconds = options.retryAfterSeconds;
  }

  get isRateLimited(): boolean {
    return this.status === 429;
  }
}

const SUBMIT_TIMEOUT_MS = 15_000;

/**
 * Submits an inquiry to the same-origin proxy (`/api/inquiries`).
 *
 * The browser never learns the address of the backend API: the proxy adds the idempotency key,
 * enforces a rate limit and forwards the request server-to-server. Errors are normalised into
 * {@link InquiryApiError} so the form can map them onto the right inputs.
 */
export async function submitInquiry(
  payload: InquiryPayload,
  idempotencyKey: string
): Promise<SubmissionResult> {
  let response: Response;
  try {
    response = await fetch("/api/inquiries", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Idempotency-Key": idempotencyKey
      },
      body: JSON.stringify(payload),
      cache: "no-store",
      signal: AbortSignal.timeout(SUBMIT_TIMEOUT_MS)
    });
  } catch (error) {
    const timedOut =
      error instanceof DOMException && (error.name === "TimeoutError" || error.name === "AbortError");
    throw new InquiryApiError(
      timedOut
        ? "The request took too long and was cancelled. Please try again."
        : "We could not reach the server. Please check your connection and try again.",
      { status: 0 }
    );
  }

  const body: unknown = await readJson(response);

  if (!response.ok) {
    throw new InquiryApiError(messageFrom(body) ?? "We could not submit your inquiry right now.", {
      status: response.status,
      issues: issuesFrom(body),
      requestId: stringField(body, "requestId"),
      retryAfterSeconds: parseRetryAfter(response.headers.get("retry-after"))
    });
  }

  const reference = stringField(body, "reference");
  if (!reference) {
    throw new InquiryApiError("The server returned an unexpected response.", { status: 502 });
  }

  return {
    reference,
    status: stringField(body, "status") ?? "RECEIVED",
    receivedAt: stringField(body, "receivedAt") ?? new Date().toISOString()
  };
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return (await response.json()) as unknown;
  } catch {
    return null;
  }
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function stringField(value: unknown, key: string): string | undefined {
  const field = asRecord(value)?.[key];
  return typeof field === "string" && field.length > 0 ? field : undefined;
}

function messageFrom(value: unknown): string | undefined {
  return stringField(value, "detail") ?? stringField(value, "message");
}

function issuesFrom(value: unknown): FieldIssue[] {
  const errors = asRecord(value)?.["errors"];
  if (!Array.isArray(errors)) {
    return [];
  }
  return errors.flatMap((entry) => {
    const record = asRecord(entry);
    const field = record?.["field"];
    const message = record?.["message"];
    if (typeof field === "string" && typeof message === "string") {
      return [{ field, message }];
    }
    return [];
  });
}

function parseRetryAfter(header: string | null): number | undefined {
  if (!header) {
    return undefined;
  }
  const seconds = Number.parseInt(header, 10);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : undefined;
}
