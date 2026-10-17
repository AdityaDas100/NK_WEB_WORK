import type { Metadata, Viewport } from "next";

import "./globals.css";

import { Footer } from "@/components/site/Footer";
import { Header } from "@/components/site/Header";
import { siteConfig } from "@/lib/site";

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: `${siteConfig.company} — ${siteConfig.tagline}`,
    template: `%s · ${siteConfig.shortName}`
  },
  description: siteConfig.description,
  applicationName: siteConfig.company,
  keywords: [
    "pharmaceutical manufacturer",
    "generic medicines",
    "contract manufacturing",
    "WHO-GMP",
    "EU-GMP",
    "product inquiry"
  ],
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    url: siteUrl,
    siteName: siteConfig.company,
    title: `${siteConfig.company} — ${siteConfig.tagline}`,
    description: siteConfig.description,
    locale: "en_US"
  },
  twitter: {
    card: "summary_large_image",
    title: `${siteConfig.company} — ${siteConfig.tagline}`,
    description: siteConfig.description
  },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, "max-image-preview": "large" }
  },
  // Phone numbers are rendered as links; disable the automatic detection that iOS Safari applies.
  formatDetection: { telephone: false, address: false, email: false }
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  colorScheme: "light",
  themeColor: "#325394"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="flex min-h-dvh flex-col bg-white">
        <Header />
        <main id="main" className="flex-1">
          {children}
        </main>
        <Footer />
      </body>
    </html>
  );
}
