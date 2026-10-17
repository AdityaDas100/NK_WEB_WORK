/**
 * Streaming placeholder.
 *
 * The skeleton mirrors the real layout (fixed heights for the hero, the catalogue grid and the form),
 * so the swap to rendered content does not move anything – this is the one place where a shift would
 * otherwise be very visible on slow connections.
 */
export default function Loading() {
  return (
    <div aria-busy="true" aria-live="polite">
      <span className="sr-only">Loading the page…</span>

      <div className="border-b border-ink-100 bg-gradient-to-b from-brand-50 via-white to-white">
        <div className="mx-auto grid max-w-7xl gap-12 px-4 py-14 sm:px-6 lg:grid-cols-[1.05fr_0.95fr] lg:gap-16 lg:px-8 lg:py-20">
          <div className="min-w-0 animate-pulse space-y-4">
            <div className="h-4 w-40 rounded bg-ink-100" />
            <div className="h-12 w-full max-w-xl rounded bg-ink-100" />
            <div className="h-12 w-4/5 max-w-lg rounded bg-ink-100" />
            <div className="h-4 w-full max-w-lg rounded bg-ink-100" />
            <div className="h-4 w-2/3 max-w-md rounded bg-ink-100" />
            <div className="flex gap-3 pt-4">
              <div className="h-12 w-52 rounded-xl bg-ink-100" />
              <div className="h-12 w-40 rounded-xl bg-ink-100" />
            </div>
          </div>
          <div className="min-h-[22rem] animate-pulse rounded-3xl border border-ink-200/80 bg-white p-8">
            <div className="h-4 w-48 rounded bg-ink-100" />
            <div className="mt-6 space-y-5">
              {[0, 1, 2].map((index) => (
                <div key={index} className="flex gap-4">
                  <div className="h-11 w-11 shrink-0 rounded-xl bg-ink-100" />
                  <div className="min-w-0 flex-1 space-y-2">
                    <div className="h-4 w-40 rounded bg-ink-100" />
                    <div className="h-4 w-full rounded bg-ink-100" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        <div className="h-4 w-32 rounded bg-ink-100" />
        <div className="mt-4 h-10 w-2/3 max-w-xl rounded bg-ink-100" />
        <ul className="mt-10 grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((index) => (
            <li key={index} className="min-h-80 animate-pulse rounded-2xl border border-ink-200/80 bg-white" />
          ))}
        </ul>
      </div>
    </div>
  );
}
