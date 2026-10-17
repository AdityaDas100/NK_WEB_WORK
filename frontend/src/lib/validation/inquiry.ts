import { z } from "zod";

/**
 * The single source of truth for what a valid inquiry looks like.
 *
 * It is used three times, which is the point:
 *   1. in the browser, through react-hook-form, to give instant feedback;
 *   2. again in the `/api/inquiries` route handler, because a client-side check is a UX feature and
 *      never a security control;
 *   3. mirrored by the Jakarta Bean Validation annotations on the Spring DTO, so a request that
 *      passes here cannot be rejected there for a different reason.
 *
 * Field limits are identical to the database column widths (see V1__create_inquiries.sql), which
 * makes silent truncation impossible.
 */

/** Letters (any script), marks, spaces and the punctuation that appears in real names. */
const NAME_PATTERN = /^[\p{L}\p{M}][\p{L}\p{M} .'-]*$/u;
const COMPANY_PATTERN = /^[\p{L}\p{M}0-9 .,&'()/-]*$/u;
const PHONE_PATTERN = /^\+?[0-9][0-9 ()\-./]*$/;

export const INQUIRY_LIMITS = {
  name: { min: 2, max: 80 },
  email: { max: 254 },
  phone: { min: 7, max: 24 },
  company: { max: 120 },
  message: { min: 10, max: 2000 }
} as const;

/**
 * Honeypot: a field that is hidden from humans and therefore only ever filled by naive bots.
 * It is accepted by the schema (so the bot believes it succeeded), stripped by the server and
 * never forwarded to the API.
 */
export const honeypotFieldName = "companyWebsite" as const;

export const inquirySchema = z.object({
  name: z
    .string()
    .trim()
    .min(INQUIRY_LIMITS.name.min, "Please enter your name.")
    .max(INQUIRY_LIMITS.name.max, "Name must be at most 80 characters.")
    .regex(NAME_PATTERN, "Use letters, spaces, apostrophes, hyphens and dots only."),

  email: z
    .string()
    .trim()
    .toLowerCase()
    .max(INQUIRY_LIMITS.email.max, "E-mail address is too long.")
    .email("Please enter a valid e-mail address, e.g. name@company.com."),

  phone: z
    .string()
    .trim()
    .min(INQUIRY_LIMITS.phone.min, "Please enter a reachable phone number.")
    .max(INQUIRY_LIMITS.phone.max, "Phone number must be at most 24 characters.")
    .regex(PHONE_PATTERN, "Use digits and + - ( ) . / only."),

  company: z
    .string()
    .trim()
    .max(INQUIRY_LIMITS.company.max, "Organisation must be at most 120 characters.")
    .regex(COMPANY_PATTERN, "Organisation contains unsupported characters.")
    .optional()
    .or(z.literal("")),

  message: z
    .string()
    .trim()
    .min(INQUIRY_LIMITS.message.min, "Please add a few more details (at least 10 characters).")
    .max(INQUIRY_LIMITS.message.max, "Message must be at most 2000 characters."),

  consent: z
    .boolean()
    .refine((value) => value === true, {
      message: "Please accept the privacy notice so we can process your inquiry."
    }),

  // Hidden anti-spam field; see the comment above.
  [honeypotFieldName]: z.string().max(200).optional()
});

export type InquiryInput = z.infer<typeof inquirySchema>;

/** The payload actually forwarded to the Spring API (honeypot removed, blanks normalised). */
export interface InquiryPayload {
  name: string;
  email: string;
  phone: string;
  company?: string;
  message: string;
  consent: boolean;
}

/** Converts validated form values into the API payload. */
export function toInquiryPayload(input: InquiryInput): InquiryPayload {
  const company = typeof input.company === "string" ? input.company.trim() : "";
  return {
    name: input.name.trim(),
    email: input.email.trim(),
    phone: input.phone.trim(),
    ...(company.length > 0 ? { company } : {}),
    message: input.message.trim(),
    consent: input.consent === true
  };
}
