"use client";

import { useEffect, useRef, useState, type ComponentType } from "react";

import { yieldToMain } from "@/lib/yield";

/**
 * Deferral boundary for the inquiry form.
 *
 * Why this exists: the form pulls in Zod, react-hook-form and the resolver — roughly 155 kB of
 * the homepage's initial JavaScript — only to validate a below-the-fold form the visitor may never
 * touch. Evaluating and hydrating that code inside the load window is what stretches the main
 * thread into long tasks. Deferring it moves that work to an idle gap (or to just before the
 * visitor scrolls near it), keeping the load window free for paint and interaction.
 *
 * Layout guarantees (same zero-shift contract as the rest of the page):
 *  - the wrapper reserves `min-h-[30rem]`, exactly the height of the form and of its success
 *    panel, so the skeleton-to-form swap cannot move anything below it;
 *  - the skeleton is announced as busy to assistive technology and never traps focus.
 *
 * Liveness guarantees:
 *  - IntersectionObserver (with a 600px preload margin) mounts the form before it scrolls into
 *    view, so fast scrollers never see a skeleton where the form should be;
 *  - `requestIdleCallback` (or a timeout where it is unavailable) mounts it even if the visitor
 *    never scrolls, so keyboard and assistive-technology users always get a working form;
 *  - without `IntersectionObserver` the form mounts immediately — old browsers get the eager
 *    behaviour, never a missing form.
 *
 * Once triggered, loading is split across two tasks on purpose (web.dev "Optimize long tasks"):
 * the dynamic `import()` evaluates the ~150 kB validation chunk in its own task, then an explicit
 * `yieldToMain()` lets a pending interaction or paint run before the mount commit lands. A plain
 * `next/dynamic` boundary cannot interpose that yield, which is why the import is manual here.
 */
export function DeferredInquiryForm() {
  const [active, setActive] = useState(false);
  const [FormComponent, setFormComponent] = useState<ComponentType | null>(null);
  const slotRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const slot = slotRef.current;
    let observer: IntersectionObserver | null = null;
    let idleHandle = 0;
    let fallbackHandle = 0;
    let settled = false;

    const activate = () => {
      if (settled) {
        return;
      }
      settled = true;
      observer?.disconnect();
      if (idleHandle !== 0 && typeof window.cancelIdleCallback === "function") {
        window.cancelIdleCallback(idleHandle);
      }
      if (fallbackHandle !== 0) {
        window.clearTimeout(fallbackHandle);
      }
      setActive(true);
    };

    if (slot === null || typeof IntersectionObserver === "undefined") {
      activate();
      return;
    }

    observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          activate();
        }
      },
      { rootMargin: "600px 0px" }
    );
    observer.observe(slot);

    if (typeof window.requestIdleCallback === "function") {
      idleHandle = window.requestIdleCallback(() => activate(), { timeout: 3000 });
    } else {
      fallbackHandle = window.setTimeout(activate, 2500);
    }

    return () => {
      observer?.disconnect();
      if (idleHandle !== 0 && typeof window.cancelIdleCallback === "function") {
        window.cancelIdleCallback(idleHandle);
      }
      if (fallbackHandle !== 0) {
        window.clearTimeout(fallbackHandle);
      }
    };
  }, []);

  useEffect(() => {
    if (!active) {
      return;
    }
    let cancelled = false;
    import("./InquiryForm")
      .then(async (module) => {
        await yieldToMain();
        if (!cancelled) {
          // Wrapped: React would otherwise call the component as a state updater.
          setFormComponent(() => module.InquiryForm);
        }
      })
      .catch((error: unknown) => {
        console.error(
          JSON.stringify({
            level: "error",
            service: "pharma-web",
            event: "app.form_chunk_failed",
            error: error instanceof Error ? error.message : String(error)
          })
        );
      });
    return () => {
      cancelled = true;
    };
  }, [active]);

  return (
    <div ref={slotRef} className="min-h-[30rem]">
      {FormComponent === null ? <FormSkeleton /> : <FormComponent />}
    </div>
  );
}

/** Shape-matched placeholder: same reserved height as the form, no focusable content. */
function FormSkeleton() {
  return (
    <div aria-busy="true" aria-live="polite" className="min-h-[30rem] animate-pulse">
      <span className="sr-only">Loading the inquiry form…</span>
      <div className="grid grid-cols-1 gap-x-5 gap-y-4 sm:grid-cols-2" aria-hidden="true">
        {[0, 1, 2, 3].map((index) => (
          <div key={index}>
            <div className="h-4 w-24 rounded bg-ink-100" />
            <div className="mt-1.5 h-11 rounded-xl bg-ink-100" />
          </div>
        ))}
        <div className="sm:col-span-2">
          <div className="h-4 w-32 rounded bg-ink-100" />
          <div className="mt-1.5 h-40 rounded-xl bg-ink-100" />
        </div>
      </div>
      <div className="mt-6 h-12 w-full rounded-xl bg-ink-100 sm:w-48" aria-hidden="true" />
    </div>
  );
}
