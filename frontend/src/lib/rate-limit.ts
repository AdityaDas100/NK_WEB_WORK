/**
 * Sliding-window rate limiter used by the `/api/inquiries` route handler.
 *
 * This is deliberately independent from the limiter in the Spring service: an attacker who can reach
 * the Next.js server should be stopped before a request is forwarded at all, and the API enforces its
 * own limit for traffic that does not pass through this tier.
 *
 * State is per server instance. On a serverless platform this is a best-effort guard only, which is
 * why the authoritative limit lives in the backend and why an edge/WAF limit (Cloudflare, API
 * Gateway, nginx `limit_req`) should be configured in front of it for high-traffic deployments.
 */
interface Window {
  hits: number[];
  lastSeen: number;
}

export class SlidingWindowRateLimiter {
  private readonly windows = new Map<string, Window>();
  private readonly limit: number;
  private readonly windowMs: number;
  private readonly burstLimit: number;
  private readonly burstWindowMs: number;
  private readonly maxKeys: number;

  constructor(options: {
    limit: number;
    windowMs: number;
    burstLimit: number;
    burstWindowMs?: number;
    maxKeys?: number;
  }) {
    this.limit = Math.max(1, options.limit);
    this.windowMs = Math.max(1000, options.windowMs);
    this.burstLimit = Math.max(1, options.burstLimit);
    this.burstWindowMs = Math.max(100, options.burstWindowMs ?? 1000);
    this.maxKeys = Math.max(1, options.maxKeys ?? 10000);
  }

  /** @returns `allowed: false` plus how long the caller should wait before retrying. */
  check(key: string, now: number = Date.now()): { allowed: boolean; retryAfterSeconds: number } {
    this.prune(now);

    const window = this.windows.get(key) ?? { hits: [], lastSeen: now };
    window.hits = window.hits.filter((timestamp) => now - timestamp < this.windowMs);
    window.lastSeen = now;

    const burstHits = window.hits.filter((timestamp) => now - timestamp < this.burstWindowMs);
    if (burstHits.length >= this.burstLimit) {
      this.windows.set(key, window);
      return { allowed: false, retryAfterSeconds: 1 };
    }
    if (window.hits.length >= this.limit) {
      this.windows.set(key, window);
      const oldest = window.hits[0] ?? now;
      const retryAfterMs = Math.max(1000, oldest + this.windowMs - now);
      return { allowed: false, retryAfterSeconds: Math.ceil(retryAfterMs / 1000) };
    }

    window.hits.push(now);
    this.windows.set(key, window);
    return { allowed: true, retryAfterSeconds: 0 };
  }

  private prune(now: number): void {
    if (this.windows.size <= this.maxKeys) {
      return;
    }
    const idleThreshold = now - this.windowMs * 2;
    for (const [key, window] of this.windows) {
      if (window.lastSeen < idleThreshold) {
        this.windows.delete(key);
      }
    }
    // Still too many? Drop the least recently used entries instead of growing without bound.
    if (this.windows.size > this.maxKeys) {
      const sorted = [...this.windows.entries()].sort((a, b) => a[1].lastSeen - b[1].lastSeen);
      for (const [key] of sorted.slice(0, this.windows.size - this.maxKeys)) {
        this.windows.delete(key);
      }
    }
  }
}

/** Singleton used by the route handler, sized from the environment. */
declare global {
  // eslint-disable-next-line no-var
  var __inquiryRateLimiter: SlidingWindowRateLimiter | undefined;
}

export function getInquiryRateLimiter(options: {
  limit: number;
  burstLimit: number;
}): SlidingWindowRateLimiter {
  globalThis.__inquiryRateLimiter ??= new SlidingWindowRateLimiter({
    limit: options.limit,
    burstLimit: options.burstLimit,
    windowMs: 60_000
  });
  return globalThis.__inquiryRateLimiter;
}
