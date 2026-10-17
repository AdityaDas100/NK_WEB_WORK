/**
 * Cooperative yielding for the main thread (web.dev "Optimize long tasks").
 *
 * `scheduler.yield()` (Chrome/Edge 129+) ends the current task and schedules the continuation as
 * a prioritized follow-up task, letting pending input and paint run first. Everywhere else we fall
 * back to a `setTimeout(0)` macrotask: still a task boundary — so higher-priority work can
 * interleave — just without the continuation priority.
 *
 * Both the global (`scheduler`) and the method (`yield`) are feature-detected, per the article's
 * caution, and the structural type keeps this compiling on any TypeScript DOM lib. Callers must
 * `await` the result: without `await` nothing is deferred.
 */
interface SchedulerWithYield {
  readonly yield?: () => Promise<void>;
}

export function yieldToMain(): Promise<void> {
  const scheduler = (globalThis as typeof globalThis & { readonly scheduler?: SchedulerWithYield })
    .scheduler;
  if (typeof scheduler?.yield === "function") {
    return scheduler.yield();
  }
  return new Promise((resolve) => {
    setTimeout(resolve, 0);
  });
}
