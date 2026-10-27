/** What rainmaker.js reads from the publisher's page before it starts. */
export interface Settings {
  /** Where the widget API is: the origin rainmaker.js itself was loaded from (contract, D-7). */
  apiOrigin: string;
  /** Which element is the headline the reader hovers. */
  headlineSelector: string;
}

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
  };
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
