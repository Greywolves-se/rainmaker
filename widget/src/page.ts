import {DEFAULT_QUALIFIED_READING_SECONDS} from './qualified-reading';

/** What rainmaker.js reads from the publisher's page before it starts. */
export interface Settings {
  /** Where the widget API is: the origin rainmaker.js itself was loaded from (contract, D-7). */
  apiOrigin: string;
  /** Which element is the headline the reader hovers. */
  headlineSelector: string;
  /** Active reading time before the interest counts as confirmed (#218). */
  qualifiedReadingSeconds: number;
}

/** The largest threshold accepted: an hour of active reading would never be reached anyway. */
const MAX_QUALIFIED_READING_SECONDS = 3600;

/**
 * The headline on the fake-news site's article pages. A publisher with other markup sets
 * `data-headline-selector` on the script tag.
 */
export const DEFAULT_HEADLINE_SELECTOR = '[data-rainmaker-headline]';

/**
 * Reads the settings from rainmaker.js's own `<script>` tag. Returns null when the widget can't
 * know where its API is, so it doesn't start.
 */
export function readSettings(script: Element | null): Settings | null {
  if (!(script instanceof HTMLScriptElement) || !script.src) {
    return null;
  }
  const source = new URL(script.src);
  if (source.protocol !== 'http:' && source.protocol !== 'https:') {
    return null;
  }
  return {
    apiOrigin: source.origin,
    headlineSelector:
      script.dataset.headlineSelector || DEFAULT_HEADLINE_SELECTOR,
    qualifiedReadingSeconds: readSeconds(
      script.dataset.qualifiedReadingSeconds
    ),
  };
}

/**
 * `data-qualified-reading-seconds`: a whole number of seconds from 1 to an hour. Anything else
 * falls back to the default, so a typo on the publisher's page never stops the widget.
 */
function readSeconds(value: string | undefined): number {
  const seconds = /^\d+$/.test(value ?? '') ? Number(value) : NaN;
  return seconds >= 1 && seconds <= MAX_QUALIFIED_READING_SECONDS
    ? seconds
    : DEFAULT_QUALIFIED_READING_SECONDS;
}

/**
 * The article's key (contract, D-2): the canonical link exactly as the page gives it, otherwise
 * the address without query string and fragment.
 */
export function articleUrl(page: Document): string {
  const canonical = page
    .querySelector('link[rel="canonical"]')
    ?.getAttribute('href');
  if (canonical && /^https?:\/\//.test(canonical)) {
    return canonical;
  }
  return page.location.origin + page.location.pathname;
}
