import type { ReactNode } from "react";

/**
 * Section wrapper.
 *
 * The vertical rhythm lives here (`py-16 sm:py-20`), never on the individual blocks, so every band of
 * the page has the same spacing regardless of what it contains.
 */
export function Section({
  id,
  children,
  tone = "plain",
  labelledBy
}: {
  id?: string;
  children: ReactNode;
  tone?: "plain" | "muted";
  labelledBy?: string;
}) {
  return (
    <section
      id={id}
      aria-labelledby={labelledBy}
      className={tone === "muted" ? "border-b border-ink-100 bg-ink-50/40" : "border-b border-ink-100"}
    >
      <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 sm:py-20 lg:px-8">{children}</div>
    </section>
  );
}
