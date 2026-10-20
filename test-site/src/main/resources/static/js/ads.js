/**
 * Fills the empty ad slots with creatives, like a real ad server would.
 *
 * Why the random delay? Real ads arrive after an auction, typically 0.3-1.5 s
 * after the page. Slots without reserved space then push the content down
 * (layout shift). The test site keeps one such "badly behaved" slot on
 * purpose, so Rainmaker can be tested on pages that move.
 *
 * If /ads/creatives.json cannot be loaded, the slots simply stay empty and the
 * page works normally.
 */
const MIN_DELAY_MS = 300;
const MAX_DELAY_MS = 1500;

export async function initAds() {
  const slots = [...document.querySelectorAll('[data-ad-slot]')];
  if (slots.length === 0) {
    return;
  }

  let creatives;
  try {
    const response = await fetch('/ads/creatives.json');
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    creatives = (await response.json()).creatives;
  } catch (error) {
    for (const slot of slots) {
      slot.dataset.adState = 'failed';
    }
    console.warn('[vintrabladet] ads could not be loaded', error);
    return;
  }

  const creativesById = new Map(creatives.map((creative) => [creative.id, creative]));
  for (const slot of slots) {
    const delay = MIN_DELAY_MS + Math.random() * (MAX_DELAY_MS - MIN_DELAY_MS);
    setTimeout(() => fillSlot(slot, creativesById.get(slot.dataset.adCreativeId)), delay);
  }
}

function fillSlot(slot, creative) {
  const container = slot.querySelector('[data-ad-container]');
  if (container === null || creative === undefined) {
    slot.dataset.adState = 'empty';
    return;
  }
  container.replaceChildren(buildCreative(creative));
  slot.dataset.adState = 'filled';
}

/**
 * Builds the creative with textContent only (never innerHTML), so the text in
 * ads.json can never inject markup.
 */
function buildCreative(creative) {
  const element = (tagName, className, text) => {
    const node = document.createElement(tagName);
    node.className = className;
    if (text !== undefined) {
      node.textContent = text;
    }
    return node;
  };

  const root = element('div', `ad-creative ad-creative--${creative.theme}`);
  root.append(
    element('span', 'ad-creative__advertiser', creative.advertiser),
    element('strong', 'ad-creative__headline', creative.headline),
    element('span', 'ad-creative__text', creative.text),
    element('span', 'ad-creative__cta', creative.callToAction),
  );
  return root;
}
