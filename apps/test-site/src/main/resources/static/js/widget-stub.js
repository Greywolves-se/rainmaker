/**
 * Stand-in for rainmaker.js, used only to test the INSTALLATION on this site
 * before the real widget exists. It shows no panel.
 *
 * Try it (PowerShell):
 *   $env:RAINMAKER_SCRIPT_URL = 'http://localhost:3000/static/js/widget-stub.js'
 *   .\mvnw.cmd -pl apps/test-site spring-boot:run     (from the repository root)
 * and add $env:RAINMAKER_INSTALL_METHOD = 'tag-manager' to load it through
 * the tag manager instead. Open the browser console to see what it found.
 *
 * What it checks - the same things the real widget will need:
 *  - that it was loaded at all (data-rainmaker-loaded on <html>)
 *  - that it can find the headline (data-rainmaker-headline)
 *  - that it can read the reader's consent through __tcfapi
 */
(function rainmakerStub() {
  'use strict';

  var root = document.documentElement;
  root.setAttribute('data-rainmaker-loaded', 'true');

  var headline = document.querySelector('[data-rainmaker-headline]');
  var article = document.querySelector('[data-article-id]');
  console.info(
    '[rainmaker stub] loaded.',
    headline ? 'Headline found, article ' + article.getAttribute('data-article-id') + '.' : 'No article on this page.'
  );

  if (typeof window.__tcfapi !== 'function') {
    console.info('[rainmaker stub] no consent manager found');
    return;
  }
  window.__tcfapi('addEventListener', 2, function (tcData, success) {
    if (!success || !tcData) {
      return;
    }
    var granted = tcData.purpose && tcData.purpose.consents[1] === true;
    root.setAttribute('data-rainmaker-consent', granted ? 'granted' : 'denied');
    console.info('[rainmaker stub] consent status:', tcData.eventStatus, granted ? 'granted' : 'not granted');
  });
})();
