/**
 * Static site configuration.
 *
 * Everything a marketer needs to change (company details, navigation, contact points) lives here so
 * that no component has to be touched for a copy change.
 */
export const siteConfig = {
  company: "NK ETHICAL (IND) LTD",
  shortName: "NK ETHICAL",
  tagline: "Evidence-based medicines for the moments that matter",
  description:
    "NK ETHICAL (IND) LTD develops, manufactures and distributes quality-assured generic " +
    "medicines across cardiology, anti-infectives, metabolic care and respiratory therapy.",
  email: "inquiries@nkethical.example",
  phone: "+1 (555) 010-4400",
  address: "400 Meridian Park, Suite 1200, Boston, MA 02210, United States",
  hours: "Monday – Saturday, 08:00 – 20:00 (ET)",
  licensing: "WHO-GMP · EU-GMP · ISO 9001:2015 · FDA registered facilities",
  nav: [
    { href: "/#products", label: "Products" },
    { href: "/#quality", label: "Quality" },
    { href: "/#inquiry", label: "Contact" },
    { href: "/privacy", label: "Privacy" }
  ],
  /**
   * The catalogue below is illustrative demo data for a fictional manufacturer. Nothing on this page
   * is medical advice, and no product is offered for sale to consumers.
   */
  demoNotice:
    "Demonstration site: NK ETHICAL (IND) LTD is a pharmaceutical company and the product catalogue " +
    "is illustrative sample data. Nothing here is medical advice or an offer to sell."
} as const;

export type SiteConfig = typeof siteConfig;
