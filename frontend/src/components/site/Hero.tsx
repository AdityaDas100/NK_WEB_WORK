import Link from "next/link";

import type { ReactNode } from "react";

import { buttonPrimary, buttonSecondary, eyebrow } from "@/components/ui/styles";
import { ArrowRight, Flask, Globe, ShieldCheck } from "@/components/icons/Icons";
import { siteConfig } from "@/lib/site";

const proofPoints = [
  {
    icon: ShieldCheck,
    title: "Quality by design",
    body: "WHO-GMP and EU-GMP certified manufacturing with full batch traceability."
  },
  {
    icon: Flask,
    title: "In-house R&D",
    body: "Formulation development, stability studies and analytical method validation."
  },
  {
    icon: Globe,
    title: "40+ markets",
    body: "Registered products and reliable supply across Europe, Asia, Africa and LATAM."
  }
] as const;

/**
 * Above-the-fold section.
 *
 * Every element is sized by its own container (`text-balance`, fixed icon boxes, a grid with equal
 * columns) rather than by loaded content, so this section renders identically on the server and
 * after hydration – no cumulative layout shift.
 */
export function Hero() {
  return (
    <section className="border-b border-ink-100 bg-gradient-to-b from-brand-50 via-white to-white">
      <div className="mx-auto grid max-w-7xl items-start gap-12 px-4 py-14 sm:px-6 lg:grid-cols-[1.05fr_0.95fr] lg:gap-16 lg:px-8 lg:py-20">
        <div className="min-w-0">
          <p className={eyebrow}>Pharmaceutical manufacturing &amp; supply</p>

          <h1 className="mt-4 text-balance text-4xl font-semibold tracking-tight text-ink-900 sm:text-5xl lg:text-[3.25rem] lg:leading-[1.08]">
            {siteConfig.tagline}
          </h1>

          <p className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-ink-600 sm:text-lg">
            {siteConfig.description}
          </p>

          <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
            <Link href="/#inquiry" className={`${buttonPrimary} sm:min-w-[13rem]`}>
              Send a product inquiry
              <ArrowRight width={18} height={18} />
            </Link>
            <Link href="/#products" className={`${buttonSecondary} sm:min-w-[11rem]`}>
              Browse catalogue
            </Link>
          </div>

          <dl className="mt-10 grid grid-cols-3 gap-4 border-t border-ink-100 pt-6">
            <Stat label="Registered products" value="180+" />
            <Stat label="Markets served" value="40+" />
            <Stat label="Batch traceability" value="100%" />
          </dl>
        </div>

        <div className="min-w-0 rounded-3xl border border-ink-200/80 bg-white p-6 shadow-card sm:p-8">
          <h2 className="text-sm font-semibold uppercase tracking-[0.16em] text-ink-500">
            Why partners choose us
          </h2>

          <ul className="mt-6 space-y-5">
            {proofPoints.map((point) => (
              <li key={point.title} className="flex gap-4">
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-700">
                  <point.icon />
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-ink-900">{point.title}</p>
                  <p className="mt-1 text-sm leading-relaxed text-ink-600">{point.body}</p>
                </div>
              </li>
            ))}
          </ul>

          <p className="mt-6 rounded-xl bg-ink-50 px-4 py-3 text-xs leading-relaxed text-ink-600">
            {siteConfig.licensing}
          </p>
        </div>
      </div>
    </section>
  );
}

function Stat({ label, value }: { label: string; value: string }): ReactNode {
  return (
    <div className="min-w-0">
      <dt className="truncate text-xs font-medium uppercase tracking-wide text-ink-500">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold tabular-nums text-brand-800">{value}</dd>
    </div>
  );
}
