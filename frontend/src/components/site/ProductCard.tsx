import Image from "next/image";

import { MedicineGlyph } from "@/components/icons/Icons";
import { badgeAmber, badgeBrand, badgeNeutral, cardSurface } from "@/components/ui/styles";
import type { Product } from "@/lib/products";

/**
 * Product card.
 *
 * Layout rules that keep the grid free of overlaps and overflow at every breakpoint:
 *  - the media slot has a fixed `aspect-[16/10]` at every breakpoint, so every card reserves
 *    identical image space whether or not a photo exists yet — adding `imageSrc` later swaps the
 *    placeholder for a photo without moving anything;
 *  - the media slot and the card both clip with `overflow-hidden`, and the photo uses
 *    `object-cover`, so an image of any dimensions fills the slot exactly with zero overflow;
 *  - on hover the card lifts (`-translate-y-1.5`) under a bold blue `shadow-card-lift`
 *    while a bold brand flood fades in over the slot (`opacity` transition, so it animates
 *    smoothly where a gradient swap would flip instantly) and the placeholder glyph turns white.
 *    The lift is transform-only (no reflow, no layout shift) and motion-gated so
 *    reduced-motion users get a static card;
 *  - the card is a flex column with `h-full`, so all cards in a row are the same height;
 *  - the footer block uses `mt-auto`, pinning badges to the bottom;
 *  - every text node either truncates (`truncate` + `title` for the full value) or clamps
 *    (`line-clamp-3`), so a long product name can never push the layout or spill out of the card;
 *  - `min-w-0` on flex children lets them shrink below their content width, which is what prevents
 *    horizontal overflow inside a grid track;
 *  - the icon sits in a fixed `h-11 w-11` box, so no glyph size can influence the card height.
 */
export function ProductCard({ product }: { product: Product }) {
  return (
    <article
      className={`group flex h-full min-w-0 flex-col overflow-hidden ${cardSurface} hover:border-brand-300 hover:shadow-card-lift motion-safe:transition-transform motion-safe:duration-200 motion-safe:hover:-translate-y-1.5`}
    >
      <div className="relative aspect-[16/10] w-full overflow-hidden bg-gradient-to-br from-brand-100/70 via-brand-50/50 to-white">
        {product.imageSrc ? (
          <>
            <Image
              src={product.imageSrc}
              alt=""
              fill
              sizes="(max-width: 640px) 100vw, (max-width: 1280px) 50vw, 33vw"
              className="object-cover"
            />
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 bg-gradient-to-t from-brand-950/90 via-brand-900/25 to-transparent opacity-0 transition-opacity duration-200 group-hover:opacity-100"
            />
          </>
        ) : (
          <>
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 bg-gradient-to-br from-brand-600 via-brand-700 to-brand-900 opacity-0 transition-opacity duration-200 group-hover:opacity-100"
            />
            <span
              aria-hidden="true"
              className="absolute inset-0 z-10 flex items-center justify-center text-brand-200 transition-colors duration-200 group-hover:text-white/85"
            >
              <MedicineGlyph form={product.form} width={64} height={64} />
            </span>
          </>
        )}
      </div>

      <div className="flex items-start gap-3 border-b border-ink-100 bg-gradient-to-br from-brand-50/70 to-white p-5">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-white text-brand-700 ring-1 ring-brand-100">
          <MedicineGlyph form={product.form} />
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="truncate text-base font-semibold text-ink-900" title={product.name}>
            {product.name}
          </h3>
          <p className="truncate text-sm text-ink-500" title={product.genericName}>
            {product.genericName}
          </p>
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-3 p-5">
        <SpecTerm label="Form" value={product.form} />
        <SpecTerm label="Strength" value={product.strength} />
        <SpecTerm label="Pack" value={product.packSize} />
        <SpecTerm label="Shelf life" value={`${product.shelfLifeMonths} months`} />
        <SpecTerm label="Storage" value={product.storage} wide />
      </dl>

      <div className="mt-auto flex flex-col gap-4 border-t border-ink-100 p-5">
        <p className="line-clamp-3 text-sm leading-relaxed text-ink-600">{product.indication}</p>

        <ul className="flex flex-wrap gap-2">
          <li>
            <span className={product.prescriptionOnly ? badgeAmber : badgeBrand}>
              {product.prescriptionOnly ? "Prescription only" : "Over the counter"}
            </span>
          </li>
          {product.registrations.map((registration) => (
            <li key={registration}>
              <span className={badgeNeutral}>{registration}</span>
            </li>
          ))}
        </ul>
      </div>
    </article>
  );
}

function SpecTerm({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={wide ? "col-span-2 min-w-0" : "min-w-0"}>
      <dt className="text-[0.68rem] font-semibold uppercase tracking-wide text-ink-400">{label}</dt>
      <dd className="truncate text-sm font-medium text-ink-700" title={value}>
        {value}
      </dd>
    </div>
  );
}
