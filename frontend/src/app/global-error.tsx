"use client";

import { useEffect } from "react";

/**
 * Last-resort error boundary: rendered when the root layout itself fails, so it has to provide the
 * `<html>` and `<body>` elements. Styling is inline because the global stylesheet may not have been
 * applied in this situation.
 */
export default function GlobalError({
  error,
  reset
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(
      JSON.stringify({
        level: "error",
        service: "pharma-web",
        event: "app.render_failed",
        digest: error.digest ?? null
      })
    );
  }, [error]);

  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, sans-serif",
          background: "#ffffff",
          color: "#23303c"
        }}
      >
        <main style={{ maxWidth: "34rem", padding: "2rem", textAlign: "center" }}>
          <h1 style={{ fontSize: "1.5rem", fontWeight: 600, margin: "0 0 0.75rem" }}>
            The application could not be started
          </h1>
          <p style={{ fontSize: "0.95rem", lineHeight: 1.6, color: "#46596b", margin: "0 0 1.5rem" }}>
            Please reload the page. If the problem persists, contact us by e-mail — the inquiry form
            may be unavailable.
          </p>
          {error.digest ? (
            <p style={{ fontFamily: "monospace", fontSize: "0.75rem", color: "#7d94a8" }}>
              Reference: {error.digest}
            </p>
          ) : null}
          <button
            type="button"
            onClick={reset}
            style={{
              marginTop: "1rem",
              height: "2.75rem",
              padding: "0 1.5rem",
              border: "none",
              borderRadius: "0.75rem",
              background: "#325394",
              color: "#ffffff",
              fontSize: "0.9rem",
              fontWeight: 600,
              cursor: "pointer"
            }}
          >
            Reload
          </button>
        </main>
      </body>
    </html>
  );
}
