import {DEFAULT_QUALIFIED_READING_SECONDS} from './qualified-reading';

/** What rainmaker.js reads from the publisher's page before it starts. */
export interface Settings {
  /** Where the widget API is: the origin rainmaker.js itself was loaded from (contract, D-7). */
  apiOrigin: string;
  /** Active reading time before the interest counts as confirmed (#218). */
  qualifiedReadingSeconds: number;
  /** Which element is the end of the article content, for the leaving signal (#219). */
  articleEndSelector: string;
  /** The place for the next step beside the article, used on desktop (#220). */
  sidebarPlaceSelector: string;
  /** The place for the next step after the article text, used on mobile (#220). */
  afterArticlePlaceSelector: string;
}

/** The largest threshold accepted: an hour of active reading would never be reached anyway. */
const MAX_QUALIFIED_READING_SECONDS = 3600;

/**
 * The end-of-article marker on the fake-news site (#217). A publisher with other markup sets
 * `data-article-end-selector` on the script tag.
 */
export const DEFAULT_ARTICLE_END_SELECTOR = '[data-rainmaker-article-end]';

/**
 * The places for the next step on the fake-news site (#217). A publisher with other markup sets
 * `data-sidebar-place-selector` and `data-after-article-place-selector` on the script tag.
 */
export const DEFAULT_SIDEBAR_PLACE_SELECTOR =
  '[data-rainmaker-next-step="sidebar"]';
export const DEFAULT_AFTER_ARTICLE_PLACE_SELECTOR =
  '[data-rainmaker-next-step="after-article"]';

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
    qualifiedReadingSeconds: readSeconds(
      script.dataset.qualifiedReadingSeconds
    ),
    articleEndSelector:
      script.dataset.articleEndSelector || DEFAULT_ARTICLE_END_SELECTOR,
    sidebarPlaceSelector:
      script.dataset.sidebarPlaceSelector || DEFAULT_SIDEBAR_PLACE_SELECTOR,
    afterArticlePlaceSelector:
      script.dataset.afterArticlePlaceSelector ||
      DEFAULT_AFTER_ARTICLE_PLACE_SELECTOR,
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
