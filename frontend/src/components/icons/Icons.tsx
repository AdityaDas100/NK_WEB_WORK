import type { DosageForm } from "@/lib/products";
import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

const baseProps: IconProps = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.6,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
  focusable: false
};

/** Brand mark: a droplet enclosing a cross, readable down to 24 px. */
export function Logo(props: IconProps) {
  return (
    <svg {...baseProps} width="32" height="32" {...props}>
      <path d="M12 3.5c3.4 3.1 6 6 6 9.2A6 6 0 0 1 6 12.7c0-3.2 2.6-6.1 6-9.2Z" />
      <path d="M12 10.5v5M9.5 13h5" />
    </svg>
  );
}

export function ShieldCheck(props: IconProps) {
  return (
    <svg {...baseProps} width="24" height="24" {...props}>
      <path d="M12 3.2 5.5 5.6v5.1c0 4.2 2.7 8 6.5 9.4 3.8-1.4 6.5-5.2 6.5-9.4V5.6L12 3.2Z" />
      <path d="m9.2 11.9 2 2 3.6-3.7" />
    </svg>
  );
}

export function Flask(props: IconProps) {
  return (
    <svg {...baseProps} width="24" height="24" {...props}>
      <path d="M9 3.5h6M10.5 3.5v5.2L6.2 17a2.6 2.6 0 0 0 2.3 3.8h7a2.6 2.6 0 0 0 2.3-3.8l-4.3-8.3V3.5" />
      <path d="M7.6 14.5h8.8" />
    </svg>
  );
}

export function Globe(props: IconProps) {
  return (
    <svg {...baseProps} width="24" height="24" {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M3.6 9.5h16.8M3.6 14.5h16.8M12 3.5c2.2 2.4 3.4 5.4 3.4 8.5S14.2 18.1 12 20.5c-2.2-2.4-3.4-5.4-3.4-8.5S9.8 5.9 12 3.5Z" />
    </svg>
  );
}

export function Truck(props: IconProps) {
  return (
    <svg {...baseProps} width="24" height="24" {...props}>
      <path d="M3.5 7.5h10v9h-10zM13.5 11h3.6l3.4 3.2v2.3h-7z" />
      <circle cx="7" cy="18" r="1.6" />
      <circle cx="17" cy="18" r="1.6" />
    </svg>
  );
}

export function Check(props: IconProps) {
  return (
    <svg {...baseProps} width="24" height="24" {...props}>
      <path d="m5 12.6 4.2 4.2L19 7" />
    </svg>
  );
}

export function AlertTriangle(props: IconProps) {
  return (
    <svg {...baseProps} width="24" height="24" {...props}>
      <path d="M12 4.5 3.7 19h16.6L12 4.5Z" />
      <path d="M12 10v4.2M12 17.2h.01" />
    </svg>
  );
}

export function Search(props: IconProps) {
  return (
    <svg {...baseProps} width="20" height="20" {...props}>
      <circle cx="10.5" cy="10.5" r="6" />
      <path d="m15.2 15.2 4.3 4.3" />
    </svg>
  );
}

export function ArrowRight(props: IconProps) {
  return (
    <svg {...baseProps} width="20" height="20" {...props}>
      <path d="M5 12h13m0 0-5.2-5.2M18 12l-5.2 5.2" />
    </svg>
  );
}

export function Spinner(props: IconProps) {
  return (
    <svg {...baseProps} width="18" height="18" className="animate-spin" {...props}>
      <circle cx="12" cy="12" r="8.5" className="opacity-25" />
      <path d="M20.5 12A8.5 8.5 0 0 0 12 3.5" />
    </svg>
  );
}

/**
 * Dosage-form glyph used on the product cards.
 *
 * All glyphs are line art drawn on the same 24 × 24 grid inside a fixed-size container, so a card can
 * never be resized by its icon and no network request (and therefore no layout shift) is required.
 */
export function MedicineGlyph({ form, ...props }: IconProps & { form: DosageForm }) {
  switch (form) {
    case "Tablet":
    case "Film-coated tablet":
      return (
        <svg {...baseProps} width="24" height="24" {...props}>
          <circle cx="12" cy="12" r="8" />
          <path d="M6.6 12h10.8" />
        </svg>
      );
    case "Capsule":
      return (
        <svg {...baseProps} width="24" height="24" {...props}>
          <rect x="3.4" y="8.2" width="17.2" height="7.6" rx="3.8" transform="rotate(-28 12 12)" />
          <path d="M11.1 7.6 15 16.4" />
        </svg>
      );
    case "Injection":
      return (
        <svg {...baseProps} width="24" height="24" {...props}>
          <path d="m14.5 3.5 6 6M17.5 6.5 8.8 15.2l-3.3 1.1 1.1-3.3 8.7-8.7M4.5 19.5l2.2-2.2" />
        </svg>
      );
    case "Oral solution":
      return (
        <svg {...baseProps} width="24" height="24" {...props}>
          <path d="M9.5 3.5h5v3l2.2 3v9a2 2 0 0 1-2 2h-5.4a2 2 0 0 1-2-2v-9l2.2-3v-3Z" />
          <path d="M7.6 14.5h8.8" />
        </svg>
      );
    case "Inhaler":
      return (
        <svg {...baseProps} width="24" height="24" {...props}>
          <path d="M6.5 20.5V9.2A3.2 3.2 0 0 1 9.7 6h4.6a1.6 1.6 0 0 1 1.6 1.6v12.9" />
          <path d="M16.5 9.5h2.8M6.5 13.8h6.6" />
        </svg>
      );
    case "Sachet":
      return (
        <svg {...baseProps} width="24" height="24" {...props}>
          <path d="M6 4.5h12v15H6zM9.5 4.5v-1h5v1" />
          <path d="M9 10h6M9 13.5h4" />
        </svg>
      );
    default:
      return (
        <svg {...baseProps} width="24" height="24" {...props}>
          <circle cx="12" cy="12" r="8" />
        </svg>
      );
  }
}
