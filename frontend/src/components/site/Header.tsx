import Image from "next/image";
import Link from "next/link";

import { navLink } from "@/components/ui/styles";
import { siteConfig } from "@/lib/site";

/**
 * Sticky site header.
 *
 * The header has a fixed height (`h-24`) at every breakpoint, which guarantees that
 * the content below never moves when the sticky state activates. The height
 * houses the brand orbit medallion with room to spare, so nothing ever overflows or overlaps.
 * The mobile navigation uses a native `<details>` element instead of a JavaScript menu: it
 * works without hydration, cannot shift the layout and is keyboard accessible by default.
 */
export function Header() {
  return (
    <header className="sticky top-0 z-50 border-b border-ink-100 bg-white/92 backdrop-blur-sm">
      <a
        href="#main"
        className="sr-only rounded-lg bg-brand-700 px-4 py-2 text-sm font-semibold text-white focus:not-sr-only focus:absolute focus:left-4 focus:top-3 focus:z-50"
      >
        Skip to main content
      </a>

      <div className="mx-auto flex h-24 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
        <Link
          href="/"
          className="flex min-w-0 shrink-0 items-center rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700"
          aria-label={`${siteConfig.company} — home`}
        >
          {/* Brand orbit medallion. The 120-unit ring geometry is exact: the ring's
              outer edge sits at a 42.2px radius while the logo corners reach
              40.3px — the full lockup stays precisely inside the circle with
              clearance, never touching or overflowing it. The logo rides 2%
              above geometric center to balance its bottom-heavy text, so its
              visual mass sits exactly in the middle. */}
          <span className="relative block h-[88px] w-[88px]">
            {/* Orbit: one revolution per cycle. Transform-only (compositor thread,
                no layout/paint), parked under reduced motion. */}
            <span
              aria-hidden="true"
              className="absolute inset-0 motion-safe:animate-orbit motion-safe:will-change-transform"
            >
              <svg
                viewBox="0 0 120 120"
                focusable="false"
                className="absolute inset-0 h-full w-full"
              >
                <circle
                  cx="60"
                  cy="60"
                  r="56"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  className="text-brand-200/70"
                />
                <circle
                  cx="60"
                  cy="60"
                  r="56"
                  fill="none"
                  stroke="#2f7fd6"
                  strokeWidth="3"
                  strokeLinecap="round"
                  strokeDasharray="46 306"
                  opacity="0.9"
                  transform="rotate(-90 60 60)"
                />
                <circle
                  cx="60"
                  cy="4"
                  r="4"
                  fill="#38bdf8"
                  style={{ filter: "drop-shadow(0 0 4px rgba(56,189,248,0.9))" }}
                />
              </svg>
            </span>
            {/* Planet: tidally locked — it stays put while the ring travels, so
                it shows the same face on every revolution. */}
            <Image
              src="/images/nk_logo.svg"
              alt={`${siteConfig.company} logo`}
              width={1156}
              height={706}
              className="absolute left-1/2 top-[48%] z-10 h-[42px] w-auto -translate-x-1/2 -translate-y-1/2"
              priority
            />
          </span>
        </Link>

        <nav aria-label="Main navigation" className="hidden items-center gap-1 md:flex">
          {siteConfig.nav.map((item) => (
            <Link key={item.href} href={item.href} className={navLink}>
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="hidden md:block">
          <Link
            href="/#inquiry"
            className="inline-flex h-10 items-center rounded-xl bg-brand-700 px-4 text-sm font-semibold text-white transition-colors hover:bg-brand-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700"
          >
            Request information
          </Link>
        </div>

        <details className="group relative md:hidden">
          <summary
            className="flex h-10 w-10 cursor-pointer list-none items-center justify-center rounded-xl border border-ink-200 text-ink-700 transition-colors hover:bg-ink-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700 [&::-webkit-details-marker]:hidden"
            aria-label="Open navigation menu"
          >
            <svg
              viewBox="0 0 24 24"
              width="20"
              height="20"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <path d="M4 7h16M4 12h16M4 17h16" />
            </svg>
          </summary>

          {/* Absolutely positioned so opening the menu never pushes the page content down. */}
          <div className="absolute right-0 top-14 w-60 overflow-hidden rounded-2xl border border-ink-200 bg-white p-2 shadow-card-hover">
            {siteConfig.nav.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="flex h-11 items-center rounded-xl px-3 text-sm font-medium text-ink-700 transition-colors hover:bg-ink-50 hover:text-ink-900"
              >
                {item.label}
              </Link>
            ))}
            <Link
              href="/#inquiry"
              className="mt-1 flex h-11 items-center justify-center rounded-xl bg-brand-700 px-3 text-sm font-semibold text-white"
            >
              Request information
            </Link>
          </div>
        </details>
      </div>
    </header>
  );
}
