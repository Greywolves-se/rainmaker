/**
 * IAB TCF v2.2 "stub" - loaded synchronously in <head>, before anything else.
 *
 * Real consent banners (CMPs) work in two steps, and so does this fake one:
 *  1. This tiny stub defines window.__tcfapi immediately. Calls made before the
 *     real consent code has loaded are queued (except "ping", answered now).
 *  2. consent.js replaces __tcfapi with the full version and answers the queue.
 *
 * This matters for Rainmaker: rainmaker.js is loaded async and may run before
 * the consent banner. It must be able to call __tcfapi at any time.
 *
 * The hidden iframe named "__tcfapiLocator" is how scripts inside iframes find
 * the CMP, as required by the TCF specification.
 *
 * Plain old JavaScript (no modules) on purpose: it must run synchronously.
 */
(function installTcfStub() {
  'use strict';

  if (typeof window.__tcfapi === 'function') {
    return;
  }

  var queue = [];

  function tcfApiStub(command, version, callback, parameter) {
    if (typeof callback !== 'function') {
      return;
    }
    if (command === 'ping') {
      callback(
        { gdprApplies: true, cmpLoaded: false, cmpStatus: 'stub', apiVersion: '2.2' },
        true
      );
      return;
    }
    queue.push([command, version, callback, parameter]);
  }

  // consent.js reads the queued calls from here.
  tcfApiStub.queue = queue;
  window.__tcfapi = tcfApiStub;

  function addLocatorFrame() {
    if (window.frames.__tcfapiLocator) {
      return;
    }
    if (!document.body) {
      // <body> does not exist yet while <head> is parsed - try again soon.
      setTimeout(addLocatorFrame, 5);
      return;
    }
    var frame = document.createElement('iframe');
    frame.name = '__tcfapiLocator';
    frame.title = '__tcfapiLocator';
    frame.hidden = true;
    document.body.appendChild(frame);
  }

  addLocatorFrame();
})();
