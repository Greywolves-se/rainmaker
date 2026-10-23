import {failOpen} from './fail-open';
import {attachHoverPanel} from './hover-panel';
import {ApprovedLink, fetchApprovedLinks} from './links-api';
import {Settings, articleUrl, readSettings} from './page';
import {createPanel} from './panel';
import {textsFor} from './translations';

/**
 * Starts the widget on the publisher's page: finds the headline and, when the reader hovers it,
 * shows the article's approved links (contract, section 7.4) in a labelled panel. On a page
 * without a headline, or without links, it does nothing and adds nothing to the page.
 *
 * @param script rainmaker.js's own `<script>` tag, which says where the widget API is
 */
export function start(script: Element | null): void {
  const settings = readSettings(script);
  if (!settings) {
    return;
  }
  // An async script in <head> can run before the headline has been parsed.
  if (document.readyState === 'loading') {
    document.addEventListener(
      'DOMContentLoaded',
      failOpen(() => watchHeadline(settings)),
      {once: true}
    );
  } else {
    watchHeadline(settings);
  }
}

function watchHeadline(settings: Settings): void {
  const headline = document.querySelector(settings.headlineSelector);
  if (!headline) {
    return;
  }
  const texts = textsFor(document.documentElement.lang);
  const url = articleUrl(document);

  // Asked once per page view, on the first hover, so a reader who never hovers costs no call.
  let links: Promise<ApprovedLink[]> | undefined;
  const loadLinks = () =>
    (links ??= fetchApprovedLinks(settings.apiOrigin, url));

  attachHoverPanel(headline, loadLinks, async () => {
    const approved = await loadLinks();
    return approved.length > 0 ? createPanel(approved, texts, headline) : null;
  });
}
