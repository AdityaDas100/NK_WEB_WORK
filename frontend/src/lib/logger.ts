import { createHash, createHmac, randomUUID } from "node:crypto";

import { serverEnv } from "@/lib/env";

/**
 * Structured, PII-free logging for the server runtime.
 *
 * Logs are emitted as one JSON object per line so that a collector (Loki, CloudWatch, Datadog,
 * Fluent Bit) can index them without a grok pattern. Personal data is never logged in clear text:
 * e-mail addresses are masked and IPs are replaced by a keyed HMAC fingerprint, mirroring the
 * behaviour of the Spring service (`PiiMasker`).
 */
type LogLevel = "info" | "warn" | "error";

type LogContext = Record<string, unknown>;

const SENSITIVE_KEY_PATTERN = /(email|phone|message|name|token|secret|authorization|cookie)/i;

function fingerprint(value: string): string {
  return createHmac("sha256", serverEnv.CLIENT_IP_HASH_SECRET).update(value).digest("hex").slice(0, 32);
}

export function maskEmail(email: string | null | undefined): string | undefined {
  if (!email) {
    return undefined;
  }
  const at = email.indexOf("@");
  return at <= 0 ? "***" : `${email.slice(0, 1)}***${email.slice(at)}`;
}

export function maskPhone(phone: string | null | undefined): string | undefined {
  if (!phone) {
    return undefined;
  }
  const digits = phone.replace(/\D/g, "");
  return digits.length <= 4 ? "***" : `***${digits.slice(-4)}`;
}

/** Replaces anything that looks sensitive before it is serialised. */
function sanitise(context: LogContext): LogContext {
  return Object.fromEntries(
    Object.entries(context).map(([key, value]) => {
      if (!SENSITIVE_KEY_PATTERN.test(key)) {
        return [key, value];
      }
      if (typeof value === "string") {
        return [key, key.toLowerCase().includes("email") ? maskEmail(value) : "***"];
      }
      return [key, "***"];
    })
  );
}

function emit(level: LogLevel, event: string, context: LogContext = {}): void {
  const line = JSON.stringify({
    timestamp: new Date().toISOString(),
    level,
    service: "pharma-web",
    event,
    ...sanitise(context)
  });

  if (level === "error") {
    console.error(line);
  } else if (level === "warn") {
    console.warn(line);
  } else {
    // The ESLint rule allows console.error/warn only; info goes through stdout directly.
    process.stdout.write(`${line}\n`);
  }
}

export const logger = {
  info: (event: string, context?: LogContext) => emit("info", event, context),
  warn: (event: string, context?: LogContext) => emit("warn", event, context),
  error: (event: string, context?: LogContext) => emit("error", event, context)
};

/** Correlation id for one request; echoed to the browser and to the API. */
export function newRequestId(): string {
  return randomUUID();
}

/** Keyed pseudonym of an IP address, used as a rate-limit key and in logs. */
export function hashClientIdentifier(value: string): string {
  return fingerprint(value);
}

/** Short digest, useful for correlating a payload without keeping it. */
export function digestOf(value: string): string {
  return createHash("sha256").update(value).digest("hex").slice(0, 12);
}
