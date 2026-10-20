/**
 * Starts the site's browser features.
 *
 * Each feature starts on its own: if one throws, the others still run and the
 * article is still readable. This is the same "fail open" rule Rainmaker
 * follows - a broken script must never take the page down with it.
 */
import { initAds } from './ads.js';
import { initConsent } from './consent.js';
import { initPaywall } from './paywall.js';
import { initStickyHeader } from './sticky-header.js';

const features = [
  ['consent', initConsent],
  ['sticky header', initStickyHeader],
  ['paywall', initPaywall],
  ['ads', initAds],
];

for (const [name, start] of features) {
  try {
    // Promise.resolve also catches errors from async features such as ads.
    Promise.resolve(start()).catch((error) => {
      console.error(`[vintrabladet] ${name} failed`, error);
    });
  } catch (error) {
    console.error(`[vintrabladet] ${name} failed`, error);
  }
}
