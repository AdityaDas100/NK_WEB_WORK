/**
 * Shared Tailwind class strings.
 *
 * Centralising them keeps the visual language consistent and makes the "no layout shift" rules
 * auditable in one place: every interactive element has a fixed height, every text block that can
 * grow has a reserved minimum height, and no element relies on content to size itself after paint.
 */

export const buttonPrimary =
  "inline-flex h-12 items-center justify-center gap-2 rounded-xl bg-brand-700 px-6 text-sm font-semibold " +
  "text-white shadow-card transition-colors duration-150 hover:bg-brand-800 " +
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700 " +
  "disabled:cursor-not-allowed disabled:bg-ink-300 disabled:text-ink-600";

export const buttonSecondary =
  "inline-flex h-12 items-center justify-center gap-2 rounded-xl border border-ink-200 bg-white px-6 " +
  "text-sm font-semibold text-ink-800 transition-colors duration-150 hover:border-brand-300 " +
  "hover:bg-brand-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700";

export const navLink =
  "inline-flex h-9 items-center rounded-lg px-3 text-sm font-medium text-ink-600 transition-colors " +
  "hover:bg-ink-50 hover:text-ink-900 focus-visible:outline-2 focus-visible:outline-offset-2 " +
  "focus-visible:outline-brand-700";

export const badgeBase =
  "inline-flex h-6 shrink-0 items-center rounded-full px-2.5 text-xs font-medium leading-none";

export const badgeNeutral = `${badgeBase} bg-ink-100 text-ink-700`;
export const badgeBrand = `${badgeBase} bg-brand-100 text-brand-800`;
export const badgeAmber = `${badgeBase} bg-amber-100 text-amber-800`;

export const cardSurface =
  "rounded-2xl border border-ink-200/80 bg-white shadow-card transition-shadow duration-150";

export const eyebrow =
  "text-xs font-semibold uppercase tracking-[0.18em] text-brand-700";

export const inputBase =
  "h-11 w-full min-w-0 rounded-xl border bg-white px-3.5 text-sm text-ink-900 shadow-sm " +
  "transition-colors duration-150 placeholder:text-ink-400 focus:outline-none focus:ring-4 " +
  "disabled:cursor-not-allowed disabled:bg-ink-50";

export const inputIdle =
  `${inputBase} border-ink-200 focus:border-brand-500 focus:ring-brand-100`;

export const inputInvalid =
  `${inputBase} border-red-400 focus:border-red-500 focus:ring-red-100`;

export const fieldLabel = "block text-sm font-medium text-ink-800";
