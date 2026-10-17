import { Check, Flask, Globe, ShieldCheck, Truck } from "@/components/icons/Icons";
import { cardSurface, eyebrow } from "@/components/ui/styles";

const pillars = [
  {
    icon: ShieldCheck,
    title: "Regulatory compliance",
    body: "WHO-GMP and EU-GMP certified sites, audited annually, with FDA-registered facilities.",
    items: ["Site master file", "Supplier qualification", "Annual product reviews"]
  },
  {
    icon: Flask,
    title: "Analytical control",
    body: "Every batch is released only after full in-house analytical testing against its specification.",
    items: ["HPLC & GC methods", "Stability programmes", "Reference standards"]
  },
  {
    icon: Truck,
    title: "Cold-chain & distribution",
    body: "Validated transport lanes with temperature monitoring from the warehouse to the market.",
    items: ["GDP compliant lanes", "Temperature loggers", "Serialisation ready"]
  },
  {
    icon: Globe,
    title: "Registrations & dossiers",
    body: "A dedicated regulatory affairs team manages dossiers and lifecycle variations.",
    items: ["CTD submissions", "Market maintenance", "Pharmacovigilance support"]
  }
] as const;

/** Quality section: reassurance for procurement partners, in a fixed 2 × 2 grid. */
export function QualitySection() {
  return (
    <div className="min-w-0">
      <div className="max-w-3xl">
        <p className={eyebrow}>Quality &amp; compliance</p>
        <h2 className="mt-3 text-balance text-3xl font-semibold tracking-tight text-ink-900 sm:text-4xl">
          A quality management system our partners can audit
        </h2>
        <p className="mt-4 text-pretty text-base leading-relaxed text-ink-600">
          Quality is not a department here: it is the process by which a molecule becomes a medicine
          that a patient can rely on. Our systems are designed to be inspected, and our records to be
          verified.
        </p>
      </div>

      <ul className="mt-10 grid grid-cols-1 items-stretch gap-5 md:grid-cols-2">
        {pillars.map((pillar) => (
          <li key={pillar.title} className="min-w-0">
            <article className={`flex h-full min-w-0 flex-col gap-4 p-6 ${cardSurface}`}>
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-700">
                <pillar.icon />
              </span>
              <div className="min-w-0">
                <h3 className="text-base font-semibold text-ink-900">{pillar.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-600">{pillar.body}</p>
              </div>
              <ul className="mt-auto space-y-2 border-t border-ink-100 pt-4">
                {pillar.items.map((item) => (
                  <li key={item} className="flex items-center gap-2 text-sm text-ink-700">
                    <span className="flex h-4 w-4 shrink-0 items-center justify-center text-brand-600">
                      <Check width={16} height={16} />
                    </span>
                    <span className="truncate" title={item}>
                      {item}
                    </span>
                  </li>
                ))}
              </ul>
            </article>
          </li>
        ))}
      </ul>
    </div>
  );
}
