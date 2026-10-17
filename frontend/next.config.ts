import type { NextConfig } from "next";

const isProduction = process.env.NODE_ENV === "production";

/**
 * Content-Security-Policy.
 *
 * The marketing page is fully static and talks to exactly one endpoint on its own origin
 * (`/api/inquiries`), so almost everything can be locked down:
 * `default-src 'self'` also blocks external images, fonts and scripts by default.
 *
 * `'unsafe-inline'` is required for the styles because Tailwind injects a style sheet and React
 * renders inline `style` attributes. Next.js also bootstraps the app with a small inline script, so
 * `script-src` needs `'unsafe-inline'` unless nonces are wired up through middleware – see
 * docs/SECURITY.md for the hardening path (nonce + `strict-dynamic`) and for the hash based
 * alternative. `'unsafe-eval'` is added in development only (React refresh).
 */
function buildContentSecurityPolicy(): string {
  const directives: Record<string, string[]> = {
    "default-src": ["'self'"],
    "base-uri": ["'self'"],
    "form-action": ["'self'"],
    "frame-ancestors": ["'none'"],
    "object-src": ["'none'"],
    "script-src": ["'self'", "'unsafe-inline'", ...(isProduction ? [] : ["'unsafe-eval'"])],
    "style-src": ["'self'", "'unsafe-inline'"],
    "img-src": ["'self'", "data:", "blob:"],
    "font-src": ["'self'", "data:"],
    "connect-src": ["'self'", ...(isProduction ? [] : ["ws:", "wss:"])],
    "worker-src": ["'self'", "blob:"],
    "manifest-src": ["'self'"],
    "frame-src": ["'none'"]
  };
  if (isProduction) {
    directives["upgrade-insecure-requests"] = [];
  }
  return Object.entries(directives)
    .map(([directive, values]) => [directive, ...values].join(" "))
    .join("; ");
}

const securityHeaders = [
  { key: "Content-Security-Policy", value: buildContentSecurityPolicy() },
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains; preload" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()"
  },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  { key: "X-DNS-Prefetch-Control", value: "off" }
];

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // Produces a minimal server bundle for the container image (see Dockerfile).
  output: "standalone",
  compress: true,
  productionBrowserSourceMaps: false,
  async headers() {
    return [
      {
        // Every response, including static assets, carries the hardening headers.
        source: "/:path*",
        headers: securityHeaders
      },
      {
        // Inquiries must never be cached by a proxy, the CDN or the browser.
        source: "/api/inquiries",
        headers: [
          { key: "Cache-Control", value: "no-store, no-cache, must-revalidate" },
          { key: "Pragma", value: "no-cache" }
        ]
      }
    ];
  }
};

export default nextConfig;
