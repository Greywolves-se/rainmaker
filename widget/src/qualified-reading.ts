import {failOpen} from './fail-open';

/**
 * Qualified reading (#218, ADR 0005): the reader has read the article long enough for the
 * interest to count as confirmed. The first hypothesis is 60 seconds of active reading; a
 * publisher can change it with `data-qualified-reading-seconds` on the script tag.
 */
export const DEFAULT_QUALIFIED_READING_SECONDS = 60;

/**
 * Time without scrolling, a key, a tap or the pointer moving after which the reader counts as
 * idle, so a page left open on the table stops adding time. A first guess, for #222 to confirm.
 */
export const IDLE_AFTER_MS = 30_000;

/** Active time is counted in steps of one second, which is precise enough for a 60 s threshold. */
const TICK_MS = 1000;

const ACTIVITY_EVENTS = [
  'scroll',
  'keydown',
  'pointerdown',
  'pointermove',
  'touchstart',
];

/**
 * Calls `onQualified` once, when the reader has spent `seconds` of active time on the page and
 * has scrolled. Time counts only while the page is visible and the reader has been active within
 * the last {@link IDLE_AFTER_MS}; opening the page counts as activity. Nothing is stored or sent:
 * the signals stay in this page view.
 *
 * @returns a function that stops watching
 */
export function watchQualifiedReading(
  seconds: number,
  onQualified: () => void
): () => void {
  let activeMs = 0;
  let idleMs = 0;
  let scrolled = false;

  const onActivity = (event: Event) => {
    idleMs = 0;
    if (event.type === 'scroll') {
      scrolled = true;
    }
  };

  const stop = () => {
    clearInterval(timer);
    for (const type of ACTIVITY_EVENTS) {
      window.removeEventListener(type, onActivity, true);
    }
  };

  const tick = failOpen(() => {
    if (document.visibilityState === 'visible' && idleMs < IDLE_AFTER_MS) {
      activeMs += TICK_MS;
    }
    idleMs += TICK_MS;
    if (scrolled && activeMs >= seconds * 1000) {
      // Stopped first, so a callback that throws can't make it report twice.
      stop();
      onQualified();
    }
  });

  // Capture, so scrolling inside the article's own scroll container counts too; passive, so the
  // listeners never slow down the page's scrolling.
  for (const type of ACTIVITY_EVENTS) {
    window.addEventListener(type, onActivity, {capture: true, passive: true});
  }
  const timer = setInterval(tick, TICK_MS);
  return stop;
}
