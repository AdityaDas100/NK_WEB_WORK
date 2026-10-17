import type { Metadata } from "next";
import Link from "next/link";

import { buttonPrimary, buttonSecondary, eyebrow } from "@/components/ui/styles";

export const metadata: Metadata = {
  title: "Page not found",
  robots: { index: false, follow: false }
};

export default function NotFound() {
  return (
    <div className="mx-auto flex min-h-[32rem] max-w-2xl flex-col items-start justify-center gap-5 px-4 py-16 sm:px-6">
      <p className={eyebrow}>Error 404</p>
      <h1 className="text-3xl font-semibold tracking-tight text-ink-900">
        We could not find that page
      </h1>
      <p className="text-sm leading-relaxed text-ink-600">
        The link may be outdated, or the page may have been moved. The product catalogue and the
        inquiry form are both on the start page.
      </p>
      <div className="flex flex-col gap-3 sm:flex-row">
        <Link href="/" className={buttonPrimary}>
          Back to the start page
        </Link>
        <Link href="/#products" className={buttonSecondary}>
          Browse products
        </Link>
      </div>
    </div>
  );
}
