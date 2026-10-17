"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";

import { AlertTriangle, Check, Spinner } from "@/components/icons/Icons";
import { buttonPrimary, fieldLabel, inputIdle, inputInvalid } from "@/components/ui/styles";
import { InquiryApiError, submitInquiry, type SubmissionResult } from "@/lib/api/inquiries";
import {
  INQUIRY_LIMITS,
  honeypotFieldName,
  inquirySchema,
  toInquiryPayload,
  type InquiryInput
} from "@/lib/validation/inquiry";

type Status = "idle" | "success";

/** Field names the server is allowed to attach a validation error to. */
const FIELD_NAMES = ["name", "email", "phone", "company", "message", "consent"] as const;
type FieldName = (typeof FIELD_NAMES)[number];

function isFieldName(value: string): value is FieldName {
  return (FIELD_NAMES as readonly string[]).includes(value);
}

/**
 * Inquiry form.
 *
 * Layout guarantees (the brief asks for zero layout shift, overflow and overlap):
 *  - every input has a fixed height (`h-11`) and every textarea a fixed row count;
 *  - each field reserves one line (`min-h-5`) for its validation message, whether or not a message
 *    is currently displayed, so an error appearing cannot push the fields below it;
 *  - the alert slot above the submit button has a reserved minimum height;
 *  - the submit button keeps its width while the request is in flight (label plus spinner);
 *  - the success panel replaces the form inside a container with a reserved minimum height, so the
 *    page below does not jump when the form disappears.
 *
 * Security notes:
 *  - the honeypot field is hidden from users and from assistive technology (bots read the DOM);
 *  - the idempotency key is generated once per submission attempt and rotated after a success, so a
 *    double click or a retry after a timeout can never create two inquiries;
 *  - server-side field errors are mapped back onto the offending inputs.
 */
export function InquiryForm() {
  const [status, setStatus] = useState<Status>("idle");
  const [result, setResult] = useState<SubmissionResult | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [retryAfter, setRetryAfter] = useState<number | null>(null);
  const [supportReference, setSupportReference] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());

  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors, isSubmitting }
  } = useForm<InquiryInput>({
    resolver: zodResolver(inquirySchema),
    mode: "onBlur",
    defaultValues: {
      name: "",
      email: "",
      phone: "",
      company: "",
      message: "",
      consent: false,
      [honeypotFieldName]: ""
    }
  });

  const messageLength = watch("message")?.length ?? 0;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    setRetryAfter(null);
    setSupportReference(null);

    try {
      const submission = await submitInquiry(toInquiryPayload(values), idempotencyKey);
      setResult(submission);
      setStatus("success");
      // The next submission is a new inquiry and must get its own key.
      setIdempotencyKey(crypto.randomUUID());
      reset({
        name: "",
        email: "",
        phone: "",
        company: "",
        message: "",
        consent: false,
        [honeypotFieldName]: ""
      });
    } catch (error) {
      if (error instanceof InquiryApiError) {
        for (const issue of error.issues) {
          if (isFieldName(issue.field)) {
            setError(issue.field, { type: "server", message: issue.message });
          }
        }
        setFormError(error.message);
        setRetryAfter(error.retryAfterSeconds ?? null);
        setSupportReference(error.requestId ?? null);
      } else {
        setFormError("Something unexpected happened. Please try again in a moment.");
      }
    }
  });

  if (status === "success" && result) {
    return (
      <div className="flex min-h-[30rem] flex-col items-start justify-center gap-5 rounded-2xl border border-brand-200 bg-brand-50/60 p-6 sm:p-8">
        <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-700 text-white">
          <Check />
        </span>
        <div>
          <h3 className="text-lg font-semibold text-ink-900">Thank you — your inquiry is with us.</h3>
          <p className="mt-2 max-w-prose text-sm leading-relaxed text-ink-600">
            A member of our commercial team will reply within one business day. Quote the reference
            below if you need to follow up.
          </p>
        </div>

        <dl className="w-full rounded-xl border border-brand-200 bg-white p-4">
          <div className="min-w-0">
            <dt className="text-[0.68rem] font-semibold uppercase tracking-wide text-ink-400">
              Reference
            </dt>
            <dd className="mt-1 select-all break-all font-mono text-sm font-medium text-ink-900">
              {result.reference}
            </dd>
          </div>
          <div className="mt-3 min-w-0">
            <dt className="text-[0.68rem] font-semibold uppercase tracking-wide text-ink-400">
              Received
            </dt>
            <dd className="mt-1 text-sm text-ink-700">
              {formatTimestamp(result.receivedAt)}
              {result.status === "DUPLICATE" ? " (this looked like a repeat submission)" : ""}
            </dd>
          </div>
        </dl>

        <button
          type="button"
          className={buttonPrimary}
          onClick={() => {
            setStatus("idle");
            setResult(null);
          }}
        >
          Send another inquiry
        </button>
      </div>
    );
  }

  return (
    <form noValidate onSubmit={onSubmit} className="min-h-[30rem]">
      <div className="grid grid-cols-1 gap-x-5 gap-y-4 sm:grid-cols-2">
        <Field id="name" label="Full name" required error={errors.name?.message} hint="As it appears on your business card.">
          <input
            id="name"
            type="text"
            autoComplete="name"
            maxLength={INQUIRY_LIMITS.name.max}
            aria-invalid={errors.name ? true : undefined}
            aria-describedby="name-message"
            className={errors.name ? inputInvalid : inputIdle}
            {...register("name")}
          />
        </Field>

        <Field id="email" label="Work e-mail" required error={errors.email?.message} hint="We reply to this address.">
          <input
            id="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            maxLength={INQUIRY_LIMITS.email.max}
            aria-invalid={errors.email ? true : undefined}
            aria-describedby="email-message"
            className={errors.email ? inputInvalid : inputIdle}
            {...register("email")}
          />
        </Field>

        <Field id="phone" label="Phone" required error={errors.phone?.message} hint="Digits, spaces and + - ( ) . / only.">
          <input
            id="phone"
            type="tel"
            autoComplete="tel"
            inputMode="tel"
            maxLength={INQUIRY_LIMITS.phone.max}
            aria-invalid={errors.phone ? true : undefined}
            aria-describedby="phone-message"
            className={errors.phone ? inputInvalid : inputIdle}
            {...register("phone")}
          />
        </Field>

        <Field id="company" label="Organisation" error={errors.company?.message} hint="Optional — hospital, distributor or pharmacy.">
          <input
            id="company"
            type="text"
            autoComplete="organization"
            maxLength={INQUIRY_LIMITS.company.max}
            aria-invalid={errors.company ? true : undefined}
            aria-describedby="company-message"
            className={errors.company ? inputInvalid : inputIdle}
            {...register("company")}
          />
        </Field>

        <div className="min-w-0 sm:col-span-2">
          <div className="flex items-baseline justify-between gap-3">
            <label className={fieldLabel} htmlFor="message">
              How can we help?
              <span aria-hidden="true" className="text-brand-700">
                {" "}
                *
              </span>
            </label>
            <span className="text-xs tabular-nums text-ink-400" aria-hidden="true">
              {messageLength} / {INQUIRY_LIMITS.message.max}
            </span>
          </div>
          <div className="mt-1.5">
            <textarea
              id="message"
              rows={6}
              maxLength={INQUIRY_LIMITS.message.max}
              aria-invalid={errors.message ? true : undefined}
              aria-describedby="message-message"
              placeholder="Products of interest, volumes, destination market, required registrations…"
              className={`${
                errors.message ? "border-red-400 focus:border-red-500 focus:ring-red-100" : "border-ink-200 focus:border-brand-500 focus:ring-brand-100"
              } block w-full min-w-0 resize-y rounded-xl border bg-white p-3.5 text-sm leading-relaxed text-ink-900 shadow-sm placeholder:text-ink-400 focus:outline-none focus:ring-4`}
              {...register("message")}
            />
          </div>
          <p id="message-message" className="mt-1 min-h-5 text-xs leading-5" role={errors.message ? "alert" : undefined}>
            {errors.message ? (
              <span className="text-red-600">{errors.message.message}</span>
            ) : (
              <span className="text-ink-500">At least {INQUIRY_LIMITS.message.min} characters.</span>
            )}
          </p>
        </div>
      </div>

      <div className="mt-2">
        <div className="flex items-start gap-3">
          <input
            id="consent"
            type="checkbox"
            aria-invalid={errors.consent ? true : undefined}
            aria-describedby="consent-message"
            className="mt-0.5 h-[1.125rem] w-[1.125rem] shrink-0 rounded border-ink-300 text-brand-700 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700"
            {...register("consent")}
          />
          <label htmlFor="consent" className="text-sm leading-relaxed text-ink-700">
            I agree that {""}
            <Link href="/privacy" className="font-medium text-brand-700 underline underline-offset-4 hover:text-brand-800">
              NK ETHICAL (IND) LTD may process my details
            </Link>{" "}
            to answer this inquiry.
          </label>
        </div>
        <p id="consent-message" className="mt-1 min-h-5 text-xs leading-5" role={errors.consent ? "alert" : undefined}>
          {errors.consent ? <span className="text-red-600">{errors.consent.message}</span> : null}
        </p>
      </div>

      {/* Reserved slot: an alert appearing (or not) never moves the submit button. */}
      <div className="min-h-[3.5rem]">
        {formError ? (
          <div role="alert" className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3">
            <span className="mt-0.5 shrink-0 text-red-600">
              <AlertTriangle width={18} height={18} />
            </span>
            <div className="min-w-0 text-sm text-red-800">
              <p className="font-medium">{formError}</p>
              {retryAfter ? (
                <p className="mt-1 text-red-700">Please wait {retryAfter} seconds before trying again.</p>
              ) : null}
              {supportReference ? (
                <p className="mt-1 break-all text-xs text-red-700/80">
                  Support reference: <span className="font-mono">{supportReference}</span>
                </p>
              ) : null}
            </div>
          </div>
        ) : null}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <button type="submit" disabled={isSubmitting} className={`${buttonPrimary} sm:min-w-[12rem]`}>
          {isSubmitting ? (
            <>
              <Spinner />
              Sending…
            </>
          ) : (
            "Send inquiry"
          )}
        </button>
        <p className="max-w-sm text-xs leading-relaxed text-ink-500">
          Sent over an encrypted connection. We never share your details with third parties.
        </p>
      </div>

      {/* Honeypot: hidden from users, attractive to bots. Never announced to assistive technology. */}
      <div aria-hidden="true" className="pointer-events-none absolute h-px w-px overflow-hidden opacity-0">
        <label htmlFor={honeypotFieldName}>Do not fill in this field</label>
        <input
          id={honeypotFieldName}
          type="text"
          tabIndex={-1}
          autoComplete="off"
          {...register(honeypotFieldName)}
        />
      </div>
    </form>
  );
}

function Field({
  id,
  label,
  hint,
  error,
  required = false,
  children
}: {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="min-w-0">
      <label className={fieldLabel} htmlFor={id}>
        {label}
        {required ? (
          <span aria-hidden="true" className="text-brand-700">
            {" "}
            *
          </span>
        ) : null}
      </label>
      <div className="mt-1.5">{children}</div>
      <p id={`${id}-message`} className="mt-1 min-h-5 text-xs leading-5" role={error ? "alert" : undefined}>
        {error ? <span className="text-red-600">{error}</span> : hint ? <span className="text-ink-500">{hint}</span> : null}
      </p>
    </div>
  );
}

function formatTimestamp(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC"
  }).format(parsed);
}
