import Link from "next/link";

import { Logo } from "@/components/icons/Icons";
import { siteConfig } from "@/lib/site";

export function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="border-t border-ink-100 bg-ink-50/60">
      <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-10 md:grid-cols-4">
          <div className="min-w-0 md:col-span-2">
            <div className="flex items-center gap-2.5">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-700 text-white">
                <Logo width={22} height={22} />
              </span>
              <span className="text-sm font-semibold text-ink-900">{siteConfig.company}</span>
            </div>
            <p className="mt-4 max-w-md text-sm leading-relaxed text-ink-600">
              {siteConfig.description}
            </p>
            <p className="mt-4 text-xs font-medium uppercase tracking-[0.14em] text-brand-700">
              {siteConfig.licensing}
            </p>
          </div>

          <div className="min-w-0">
            <h2 className="text-xs font-semibold uppercase tracking-[0.16em] text-ink-500">
              Explore
            </h2>
            <ul className="mt-4 space-y-2">
              {siteConfig.nav.map((item) => (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    className="text-sm text-ink-700 underline-offset-4 hover:text-brand-800 hover:underline"
                  >
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div className="min-w-0">
            <h2 className="text-xs font-semibold uppercase tracking-[0.16em] text-ink-500">
              Contact
            </h2>
            <ul className="mt-4 space-y-2 text-sm text-ink-700">
              <li className="break-words">{siteConfig.address}</li>
              <li>
                <a
                  href={`mailto:${siteConfig.email}`}
                  className="underline-offset-4 hover:text-brand-800 hover:underline"
                >
                  {siteConfig.email}
                </a>
              </li>
              <li>
                <a
                  href={`tel:${siteConfig.phone.replace(/[^+\d]/g, "")}`}
                  className="underline-offset-4 hover:text-brand-800 hover:underline"
                >
                  {siteConfig.phone}
                </a>
              </li>
              <li className="text-ink-500">{siteConfig.hours}</li>
            </ul>
          </div>
        </div>

        <div className="mt-10 flex flex-col gap-3 border-t border-ink-200 pt-6 text-xs text-ink-500 sm:flex-row sm:items-center sm:justify-between">
          <p>© {year} {siteConfig.company}. All rights reserved.</p>
          <p className="max-w-2xl text-pretty">{siteConfig.demoNotice}</p>
        </div>
      </div>
    </footer>
  );
}
