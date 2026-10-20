/**
 * Fake web statistics ("Vintra Stats") - a stand-in for the analytics scripts
 * every news site runs.
 *
 * The tag manager only loads it after the reader has accepted all cookies,
 * which lets you check that consent-based loading works. It sends nothing
 * anywhere: it only records a page view in the dataLayer and marks the page.
 */
(function recordPageView() {
  'use strict';
  window.dataLayer = window.dataLayer || [];
  window.dataLayer.push({ event: 'page_view', path: window.location.pathname });
  document.documentElement.setAttribute('data-stats-loaded', 'true');
})();
