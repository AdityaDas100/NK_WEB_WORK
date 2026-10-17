"use client";

import { useEffect } from "react";

import { AlertTriangle } from "@/components/icons/Icons";
import { buttonPrimary, buttonSecondary } from "@/components/ui/styles";

/**
 * Route level error boundary.
 *
 * Renders a stable, fixed-height panel (so a failure cannot reflow the page) and logs the error with
 * its digest, which is the only identifier the server side exposes for correlation – the error
 * message and stack trace stay on the server.
 */
export default function ErrorBoundary({
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
        event: "ui.render_failed",
        digest: error.digest ?? null,
        name: error.name
      })
    );
  }, [error]);

  return (
    <div className="mx-auto flex min-h-[32rem] max-w-2xl flex-col items-start justify-center gap-5 px-4 py-16 sm:px-6">
      <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-red-50 text-red-600">
        <AlertTriangle />
      </span>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink-900">
          This section could not be displayed
        </h1>
        <p className="mt-3 text-sm leading-relaxed text-ink-600">
          Something went wrong while rendering the page. You can retry, and if the problem persists
          please contact us by e-mail instead of using the form.
        </p>
        {error.digest ? (
          <p className="mt-2 break-all font-mono text-xs text-ink-400">Reference: {error.digest}</p>
        ) : null}
      </div>
      <div className="flex flex-col gap-3 sm:flex-row">
        <button type="button" onClick={reset} className={buttonPrimary}>
          Try again
        </button>
        <a href="/" className={buttonSecondary}>
          Back to the start page
        </a>
      </div>
    </div>
  );
}
