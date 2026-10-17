import Link from "next/link";

import { AlertTriangle, ArrowRight, Check, ShieldCheck } from "@/components/icons/Icons";
import { Hero } from "@/components/site/Hero";
import { DeferredInquiryForm } from "@/components/site/DeferredInquiryForm";
import { ProductCatalog } from "@/components/site/ProductCatalog";
import { QualitySection } from "@/components/site/QualitySection";
import { Section } from "@/components/site/Section";
import { buttonSecondary, cardSurface, eyebrow } from "@/components/ui/styles";
import { products } from "@/lib/products";
import { siteConfig } from "@/lib/site";

/**
 * Landing page.
 *
 * Server rendered: the catalogue markup and the contact details are part of the initial HTML, so the
 * page is complete (and stable) before React hydrates. Only the two interactive islands – the
 * catalogue filters and the inquiry form – are client components.
 */
export default function HomePage() {
  return (
    <>
      <Hero />

      <Section id="products" labelledBy="products-heading">
        <div className="max-w-3xl">
          <p className={eyebrow}>Product catalogue</p>
          <h2
            id="products-heading"
            className="mt-3 text-balance text-3xl font-semibold tracking-tight text-ink-900 sm:text-4xl"
          >
            Medicines manufactured to specification
          </h2>
          <p className="mt-4 text-pretty text-base leading-relaxed text-ink-600">
            A representative selection of our portfolio across six therapeutic areas. Every product is
            available in multiple pack sizes and market-specific artwork; ask us for the full dossier,
            stability data and artwork options for your registration.
          </p>
        </div>

        <div className="mt-10">
          <ProductCatalog products={products} />
        </div>

        <p className="mt-8 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-xs leading-relaxed text-amber-900">
          <span className="mt-0.5 shrink-0 text-amber-700">
            <AlertTriangle width={18} height={18} />
          </span>
          <span>
            Prescription-only medicines are supplied exclusively to licensed wholesalers, pharmacies
            and healthcare institutions. Nothing on this page is medical advice; product information
            is provided for professional procurement purposes only.
          </span>
        </p>
      </Section>

      <Section id="quality" tone="muted" labelledBy="quality-heading">
        <h2 id="quality-heading" className="sr-only">
          Quality and compliance
        </h2>
        <QualitySection />
      </Section>

      <Section id="inquiry" labelledBy="inquiry-heading">
        <div className="grid grid-cols-1 gap-10 lg:grid-cols-[0.85fr_1.15fr] lg:gap-16">
          <div className="min-w-0">
            <p className={eyebrow}>Product inquiry</p>
            <h2
              id="inquiry-heading"
              className="mt-3 text-balance text-3xl font-semibold tracking-tight text-ink-900 sm:text-4xl"
            >
              Talk to our commercial team
            </h2>
            <p className="mt-4 text-pretty text-base leading-relaxed text-ink-600">
              Tell us which products, volumes and markets you are interested in. We answer every
              inquiry within one business day, and we will follow up with a price indication, lead time
              and documentation pack where applicable.
            </p>

            <ul className="mt-8 space-y-4">
              {[
                "Dedicated account manager per market",
                "Dossiers, CoAs and stability data on request",
                "Sample quantities for qualification testing"
              ].map((item) => (
                <li key={item} className="flex items-start gap-3 text-sm text-ink-700">
                  <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-100 text-brand-700">
                    <Check width={14} height={14} />
                  </span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>

            <div className={`mt-8 p-5 ${cardSurface}`}>
              <h3 className="flex items-center gap-2 text-sm font-semibold text-ink-900">
                <span className="text-brand-700">
                  <ShieldCheck width={18} height={18} />
                </span>
                Direct contact
              </h3>
              <dl className="mt-4 space-y-2 text-sm">
                <div className="flex flex-wrap gap-x-2">
                  <dt className="text-ink-500">E-mail:</dt>
                  <dd className="min-w-0 break-words">
                    <a
                      href={`mailto:${siteConfig.email}`}
                      className="font-medium text-brand-700 underline-offset-4 hover:underline"
                    >
                      {siteConfig.email}
                    </a>
                  </dd>
                </div>
                <div className="flex flex-wrap gap-x-2">
                  <dt className="text-ink-500">Phone:</dt>
                  <dd className="font-medium text-ink-800">{siteConfig.phone}</dd>
                </div>
                <div className="flex flex-wrap gap-x-2">
                  <dt className="text-ink-500">Hours:</dt>
                  <dd className="font-medium text-ink-800">{siteConfig.hours}</dd>
                </div>
              </dl>
            </div>

            <Link href="/privacy" className={`${buttonSecondary} mt-6`}>
              How we handle your data
              <ArrowRight width={18} height={18} />
            </Link>
          </div>

          <div className={`min-w-0 p-6 sm:p-8 ${cardSurface}`}>
            <h3 className="text-lg font-semibold text-ink-900">Send an inquiry</h3>
            <p className="mt-2 text-sm leading-relaxed text-ink-600">
              All fields marked with <span className="text-brand-700">*</span> are required. Your
              details are transmitted over an encrypted connection and stored in our EU-hosted
              database.
            </p>

            <div className="mt-6">
              <DeferredInquiryForm />
            </div>
          </div>
        </div>
      </Section>
    </>
  );
}
