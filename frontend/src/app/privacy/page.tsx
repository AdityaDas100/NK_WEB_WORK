import type { Metadata } from "next";
import Link from "next/link";

import { buttonSecondary, cardSurface, eyebrow } from "@/components/ui/styles";
import { siteConfig } from "@/lib/site";

export const metadata: Metadata = {
  title: "Privacy notice",
  description:
    "How NK ETHICAL (IND) LTD collects, uses and protects the personal data submitted through the product inquiry form.",
  alternates: { canonical: "/privacy" }
};

const sections = [
  {
    title: "What we collect",
    body: [
      "When you submit the inquiry form we store the name, e-mail address, phone number and message you provide, plus the optional organisation name and whether you agreed to this notice.",
      "We also store technical metadata required to secure the endpoint: a keyed, one-way fingerprint of your IP address (never the address itself) and the user agent string of your browser."
    ]
  },
  {
    title: "Why we process it",
    body: [
      "Your details are used for one purpose only: answering your inquiry, including follow-up questions about products, documentation and commercial terms.",
      "The technical metadata is processed on the basis of our legitimate interest in protecting the service against spam, scraping and denial-of-service attempts."
    ]
  },
  {
    title: "How long we keep it",
    body: [
      "Inquiries are retained for 24 months after the last contact and are then deleted, unless a business relationship exists that requires us to keep the correspondence longer under commercial or regulatory record-keeping obligations."
    ]
  },
  {
    title: "Who can see it",
    body: [
      "Access is limited to the commercial team that answers inquiries and to the administrators of the systems that store them. We do not sell or rent personal data, and we do not use it for advertising.",
      "Our cloud providers process the data strictly on our instructions under a data processing agreement."
    ]
  },
  {
    title: "How we protect it",
    body: [
      "Traffic is encrypted in transit (TLS 1.2+ with HSTS). Submissions are validated and sanitised, rate limited and stored in an access-controlled PostgreSQL database.",
      "Application logs are designed never to contain personal data in clear text: e-mail addresses are masked and IP addresses are replaced by keyed fingerprints."
    ]
  },
  {
    title: "Your rights",
    body: [
      "You can request access to, correction of, or deletion of the data we hold about you, and you can object to further processing. Write to the address below and we will respond within 30 days."
    ]
  }
];

export default function PrivacyPage() {
  return (
    <div className="mx-auto max-w-4xl px-4 py-16 sm:px-6 lg:px-8">
      <p className={eyebrow}>Legal</p>
      <h1 className="mt-3 text-balance text-3xl font-semibold tracking-tight text-ink-900 sm:text-4xl">
        Privacy notice
      </h1>
      <p className="mt-4 text-pretty text-base leading-relaxed text-ink-600">
        This notice explains, in plain language, what happens to the information you enter in the
        product inquiry form on this site.
      </p>

      <div className="mt-10 space-y-6">
        {sections.map((section) => (
          <section key={section.title} className={`p-6 ${cardSurface}`}>
            <h2 className="text-base font-semibold text-ink-900">{section.title}</h2>
            <div className="mt-3 space-y-3">
              {section.body.map((paragraph) => (
                <p key={paragraph} className="text-sm leading-relaxed text-ink-600">
                  {paragraph}
                </p>
              ))}
            </div>
          </section>
        ))}

        <section className={`p-6 ${cardSurface}`}>
          <h2 className="text-base font-semibold text-ink-900">Contact the data protection team</h2>
          <p className="mt-3 text-sm leading-relaxed text-ink-600">
            {siteConfig.company}
            <br />
            {siteConfig.address}
            <br />
            <a
              href={`mailto:${siteConfig.email}`}
              className="font-medium text-brand-700 underline-offset-4 hover:underline"
            >
              {siteConfig.email}
            </a>
          </p>
        </section>
      </div>

      <div className="mt-10">
        <Link href="/" className={buttonSecondary}>
          Back to the site
        </Link>
      </div>
    </div>
  );
}
