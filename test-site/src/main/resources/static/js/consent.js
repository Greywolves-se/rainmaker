/**
 * Connects the fake consent manager (consent-core.js) to the page:
 * replaces the stub __tcfapi, answers queued calls, shows the banner and
 * reacts to the buttons.
 *
 * The banner itself is rendered by the server (templates/fragments.html) so its text
 * comes from the translation file; this script only shows and hides it.
 */
import {
  createTcfApi,
  isConsentChoice,
  readStoredChoice,
  storeChoice,
  takeOverFromStub,
} from './consent-core.js';

/** Returns localStorage, or null if the browser blocks it. */
function safeLocalStorage() {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

export function initConsent() {
  const storage = safeLocalStorage();
  const api = createTcfApi(readStoredChoice(storage));

  // Take over from the stub and answer everything that was queued meanwhile.
  takeOverFromStub(window, api);

  const banner = document.querySelector('[data-consent-banner]');
  if (banner === null) {
    return;
  }

  const show = () => {
    banner.hidden = false;
  };
  const hide = () => {
    banner.hidden = true;
  };

  if (api.getChoice() === null) {
    show();
  }

  banner.addEventListener('click', (event) => {
    const button = event.target instanceof Element
      ? event.target.closest('[data-consent-choice]')
      : null;
    const choice = button?.getAttribute('data-consent-choice');
    if (!isConsentChoice(choice)) {
      return;
    }
    api.setChoice(choice);
    storeChoice(storage, choice);
    hide();
    // Tell the tag manager, the way real sites tell Google Tag Manager.
    window.dataLayer = window.dataLayer ?? [];
    window.dataLayer.push({ event: 'consent_update', consent: choice });
  });

  for (const opener of document.querySelectorAll('[data-consent-open]')) {
    opener.addEventListener('click', () => {
      api.showUi();
      show();
      banner.querySelector('button')?.focus();
    });
  }
}
