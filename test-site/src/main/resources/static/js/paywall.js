/**
 * The fake paywall on PLUS articles.
 *
 * The whole article text is in the HTML; CSS fades out the locked part while
 * `data-paywall-state="locked"`. The "continue reading" button just unlocks it
 * - there is no payment, this is a test site.
 */
export function initPaywall() {
  for (const button of document.querySelectorAll('[data-paywall-unlock]')) {
    button.addEventListener('click', () => {
      const container = button.closest('[data-paywall-state]');
      if (container !== null) {
        container.dataset.paywallState = 'unlocked';
      }
    });
  }
}
