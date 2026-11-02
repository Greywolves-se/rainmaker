import {failOpen} from './fail-open';
import {watchQualifiedReading} from './qualified-reading';

/**
 * The observed area: the screen, stretched far upwards. A marker counts as reached while it is on
 * the screen and after the reader has scrolled past it, even with a jump (the End key) that never
 * shows it on the screen. Only scrolling back up above it makes it unreached again.
 */
const SCREEN_AND_EVERYTHING_ABOVE = '1000000px 0px 0px 0px';

/**
 * Leaving the article (#219, ADR 0005): the reader has read it (qualified reading, #218) and has
 * reached its end, the marker the publisher puts last in the article text (test site:
 * `data-rainmaker-article-end`, #217). The two can happen in either order. When they both hold,
 * `onLeaving` is called once, the moment the next step can show (#220). A reader who stops in the
 * middle of the article never gets it.
 *
 * Nothing is stored or sent: the signals stay in this page view. Without IntersectionObserver,
 * `onLeaving` is never called.
 *
 * @param marker the end of the article content
 * @param seconds active reading time before the reading counts as qualified
 * @returns a function that stops watching
 */
export function watchLeavingArticle(
  marker: Element,
  seconds: number,
  onLeaving: () => void
): () => void {
  if (typeof IntersectionObserver === 'undefined') {
    return () => {};
  }
  let qualified = false;
  let atEnd = false;
  let stopReading = () => {};
  let stopEnd = () => {};

  const stop = () => {
    stopReading();
    stopEnd();
  };
  const signalWhenBoth = () => {
    if (qualified && atEnd) {
      // Stopped first, so a callback that throws can't make it signal twice.
      stop();
      onLeaving();
    }
  };

  stopReading = watchQualifiedReading(seconds, () => {
    qualified = true;
    signalWhenBoth();
  });
  stopEnd = watchArticleEnd(marker, reached => {
    atEnd = reached;
    signalWhenBoth();
  });
  return stop;
}

/**
 * Tells `onChange` whether the reader is at or past `marker`, each time that changes.
 *
 * @returns a function that stops watching
 */
function watchArticleEnd(
  marker: Element,
  onChange: (reached: boolean) => void
): () => void {
  const observer = new IntersectionObserver(
    failOpen((entries: IntersectionObserverEntry[]) => {
      // Several changes can arrive at once; the last one is where the reader is now.
      const latest = entries[entries.length - 1];
      if (latest) {
        onChange(latest.isIntersecting);
      }
    }),
    {rootMargin: SCREEN_AND_EVERYTHING_ABOVE}
  );
  observer.observe(marker);
  return () => observer.disconnect();
}
