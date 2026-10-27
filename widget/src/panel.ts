import type {ApprovedLink} from './links-api';
import type {Texts} from './translations';

/** Space between the headline and the panel, in pixels. */
const GAP_PX = 8;

/**
 * The panel's look. It lives in the panel's Shadow DOM, so the page's CSS can't reach it and it
 * can't reach the page. `all: initial` stops the page's inherited styles (font, colour) too.
 */
const PANEL_CSS = `
  :host { all: initial; }
  aside {
    box-sizing: border-box;
    max-width: 320px;
    padding: 12px;
    border: 1px solid #c8c8c8;
    border-radius: 6px;
    background: #ffffff;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
    color: #1a1a1a;
    font: 14px/1.4 system-ui, sans-serif;
  }
  .label { margin: 0 0 8px; color: #595959; font-size: 12px; text-transform: uppercase; }
  ul { margin: 0; padding: 0; list-style: none; }
  li + li { margin-top: 8px; }
  a { display: block; color: inherit; text-decoration: none; }
  a:hover .title, a:focus .title { text-decoration: underline; }
  .title { display: block; font-weight: 600; }
  .text, .advertiser { display: block; }
  .advertiser { color: #595959; font-size: 12px; }
`;

/**
 * The host element's own tag, so page rules for ordinary elements (e.g. {@code div { padding }}) don't
 * match it. Rules that match every element (e.g. {@code body > *}) still can; nothing a script adds
 * to someone else's page is fully out of reach.
 */
const HOST_TAG = 'rainmaker-panel';

/**
 * Builds the panel for the approved links, placed just below the headline. The returned element
 * is not on the page yet. It is positioned absolutely, so adding it moves nothing (no layout
 * shift). All text goes in as text, never as HTML.
 */
export function createPanel(
  links: ApprovedLink[],
  texts: Texts,
  headline: Element
): HTMLElement {
  const host = document.createElement(HOST_TAG);
  host.setAttribute('data-rainmaker-panel', '');
  placeBelow(host, headline);

  const shadow = host.attachShadow({mode: 'open'});
  addStyles(shadow);

  const panel = document.createElement('aside');
  panel.setAttribute('aria-label', texts.panelName);
  panel.append(element('p', 'label', texts.adLabel), linkList(links));
  shadow.append(panel);
  return host;
}

function linkList(links: ApprovedLink[]): HTMLUListElement {
  const list = document.createElement('ul');
  for (const link of links) {
    const anchor = document.createElement('a');
    anchor.href = link.clickUrl;
    anchor.rel = 'sponsored noopener';
    anchor.append(
      element('span', 'title', link.title),
      element('span', 'text', link.text),
      element('span', 'advertiser', link.advertiser)
    );
    const item = document.createElement('li');
    item.append(anchor);
    list.append(item);
  }
  return list;
}

function element(tag: string, className: string, text: string): HTMLElement {
  const created = document.createElement(tag);
  created.className = className;
  created.textContent = text;
  return created;
}

/**
 * Positions the panel through element.style, which a Content-Security-Policy with
 * `style-src 'self'` (like the fake-news site's) still allows; a style attribute would be blocked.
 */
function placeBelow(host: HTMLElement, headline: Element): void {
  const box = headline.getBoundingClientRect();
  host.style.position = 'absolute';
  host.style.top = `${box.bottom + window.scrollY + GAP_PX}px`;
  host.style.left = `${box.left + window.scrollX}px`;
  host.style.zIndex = '2147483647';
}

/**
 * A constructed stylesheet is not blocked by `style-src 'self'`, while a `<style>` element is.
 * Browsers without constructed stylesheets get the `<style>` element.
 */
function addStyles(shadow: ShadowRoot): void {
  if (
    'adoptedStyleSheets' in shadow &&
    typeof CSSStyleSheet.prototype.replaceSync === 'function'
  ) {
    const sheet = new CSSStyleSheet();
    sheet.replaceSync(PANEL_CSS);
    shadow.adoptedStyleSheets = [sheet];
    return;
  }
  const style = document.createElement('style');
  style.textContent = PANEL_CSS;
  shadow.append(style);
}
