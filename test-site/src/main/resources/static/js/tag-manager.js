/**
 * A tiny stand-in for Google Tag Manager (GTM).
 *
 * Many publishers add third-party scripts through a tag manager instead of
 * editing their page template. The architecture (P4) says rainmaker.js may be
 * installed that way, so the test site supports both:
 *
 *   RAINMAKER_INSTALL_METHOD=template     -> <script async> directly in the page
 *   RAINMAKER_INSTALL_METHOD=tag-manager  -> this script loads it
 *
 * How it works, like GTM:
 *  1. Fetches its "container" (the list of tags) from the URL in
 *     data-container-url. The server builds it from the configuration.
 *  2. Injects each tag as an async <script>:
 *       trigger "pageview" - right away
 *       trigger "consent"  - only after the reader accepted all cookies
 *         (checked through the TCF API, like GTM's consent mode)
 *  3. Pushes events to window.dataLayer.
 *
 * If anything fails the page is unaffected (fail open).
 * Plain script (not a module) because tag managers are loaded that way.
 */
(function startTagManager() {
  'use strict';

  var script = document.currentScript;
  var containerUrl = script ? script.getAttribute('data-container-url') : null;
  window.dataLayer = window.dataLayer || [];
  window.dataLayer.push({ event: 'tm.js', 'tm.start': Date.now() });

  if (!containerUrl) {
    return;
  }

  fetch(containerUrl)
    .then(function (response) {
      if (!response.ok) {
        throw new Error('HTTP ' + response.status);
      }
      return response.json();
    })
    .then(runContainer)
    .catch(function (error) {
      console.warn('[tag-manager] container could not be loaded', error);
    });

  function runContainer(container) {
    container.tags.forEach(function (tag) {
      if (tag.trigger === 'pageview') {
        injectTag(tag);
      } else if (tag.trigger === 'consent') {
        whenConsentGiven(function () {
          injectTag(tag);
        });
      }
    });
    window.dataLayer.push({ event: 'tm.loaded', containerId: container.containerId });
  }

  function injectTag(tag) {
    var element = document.createElement('script');
    element.async = true;
    element.src = tag.src;
    element.setAttribute('data-tag-name', tag.name);
    document.head.appendChild(element);
    window.dataLayer.push({ event: 'tm.tag', tagName: tag.name });
  }

  /** Runs `callback` once, as soon as the reader has consented to purpose 1. */
  function whenConsentGiven(callback) {
    if (typeof window.__tcfapi !== 'function') {
      return; // No consent manager: never fire consent-based tags.
    }
    var fired = false;
    window.__tcfapi('addEventListener', 2, function (tcData, success) {
      if (fired || !success || !tcData || !tcData.purpose) {
        return;
      }
      if (tcData.purpose.consents[1] === true) {
        fired = true;
        callback();
        window.__tcfapi('removeEventListener', 2, function () {}, tcData.listenerId);
      }
    });
  }
})();
