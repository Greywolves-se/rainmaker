/**
 * Fail open (CLAUDE.md, product rules): nothing thrown by rainmaker.js may reach the publisher's
 * page. main.ts only covers the code that runs while the script loads; event handlers and timers
 * run later, so each of them is wrapped with failOpen.
 */

/** Drops an error from the widget's own code, so it never reaches the page or its console. */
export function ignoreError(error: unknown): void {
  // TODO(#175): report the error type to S2 with the error beacon.
  void error;
}

/**
 * Wraps an event handler or timer callback so that neither a thrown error nor a rejected promise
 * escapes it.
 */
export function failOpen<Args extends unknown[]>(
  handler: (...args: Args) => void | Promise<void>
): (...args: Args) => void {
  return (...args) => {
    try {
      const result = handler(...args);
      if (result instanceof Promise) {
        result.catch(ignoreError);
      }
    } catch (error) {
      ignoreError(error);
    }
  };
}
