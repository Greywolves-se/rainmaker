import {failOpen} from './fail-open';
import {watchLeavingArticle} from './leaving-article';
import {fetchApprovedLinks} from './links-api';
import {choosePlace} from './next-step-place';
import {articleUrl, readSettings} from './page';
import type {Settings} from './page';
import {createPanel} from './panel';
import {textsFor} from './translations';

/**
 * Starts the widget on the publisher's page (ADR 0005): once the reader has read the article and
 * reaches its end (#218, #219), it asks for the article's approved links (contract, section 7.4)
 * and shows them as a labelled next step, beside the article or after it (#220). On a page
 * without an end-of-article marker or a place for the next step, or for an article without
 * links, it does nothing and adds nothing to the page.
 *
 * @param script rainmaker.js's own `<script>` tag, which says where the widget API is
 */
export function start(script: Element | null): void {
  const settings = readSettings(script);
  if (!settings) {
    return;
  }
  // An async script in <head> can run before the article has been parsed.
  if (document.readyState === 'loading') {
    document.addEventListener(
      'DOMContentLoaded',
      failOpen(() => watchArticle(settings)),
      {once: true}
    );
  } else {
    watchArticle(settings);
  }
}

function watchArticle(settings: Settings): void {
  const articleEnd = document.querySelector(settings.articleEndSelector);
  const sidebar = document.querySelector(settings.sidebarPlaceSelector);
  const afterArticle = document.querySelector(
    settings.afterArticlePlaceSelector
  );
  if (!articleEnd || (!sidebar && !afterArticle)) {
    return;
  }
  const texts = textsFor(document.documentElement.lang);
  const url = articleUrl(document);

  // Asked once, when the next step can show: a page view that never gets there makes no call
  // (ADR 0005). Without a usable answer there is no next step in this page view.
  const showNextStep = failOpen(async () => {
    const links = await fetchApprovedLinks(settings.apiOrigin, url);
    if (!links || links.length === 0) {
      return;
    }
    // Chosen now, not at start-up: the layout the reader has at the end of the article counts.
    choosePlace(sidebar, afterArticle, articleEnd)?.append(
      createPanel(links, texts)
    );
  });
  watchLeavingArticle(
    articleEnd,
    settings.qualifiedReadingSeconds,
    showNextStep
  );
}
