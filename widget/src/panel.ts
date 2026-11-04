import type {ApprovedLink} from './links-api';
import type {Texts} from './translations';

/**
 * The panel's look. It lives in the panel's Shadow DOM, so the page's CSS can't reach it and it
 * can't reach the page. `all: initial` stops the page's inherited styles (font, colour) too.
 * It fills the width of its place, which the publisher's layout decides (#220).
 */
const PANEL_CSS = `
  :host { all: initial; display: block; }
  aside {
    box-sizing: border-box;
    padding: 12px;
    border: 1px solid #c8c8c8;
    border-radius: 6px;
    background: #ffffff;
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
 * The host element's own tag, so page rules for ordinary elements (e.g. `div { padding }`) don't
 * match it. Rules that match every element (e.g. `body > *`) still can; nothing a script adds to
 * someone else's page is fully out of reach.
 */
const HOST_TAG = 'rainmaker-panel';

/**
 * Builds the next step for the approved links (#220, ADR 0005): a labelled box that goes in the
 * place the publisher marked, in the page's flow, so it never covers the article. The returned
 * element is not on the page yet. All text goes in as text, never as HTML.
 */
export function createPanel(links: ApprovedLink[], texts: Texts): HTMLElement {
  const host = document.createElement(HOST_TAG);
  host.setAttribute('data-rainmaker-panel', '');

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
 * Whether this browser can style the panel on any page (#187). The panel's CSS is a constructed
 * stylesheet, which a Content-Security-Policy with `style-src 'self'` allows; a `<style>` element
 * would be blocked there, and an unstyled ad could pass for the publisher's own content. Browsers
 * without one (Safari before 16.4, Firefox before 101) get no next step at all.
 */
export function canStylePanel(): boolean {
  return (
    typeof ShadowRoot === 'function' &&
    'adoptedStyleSheets' in ShadowRoot.prototype &&
    typeof CSSStyleSheet === 'function' &&
    typeof CSSStyleSheet.prototype.replaceSync === 'function'
  );
}

function addStyles(shadow: ShadowRoot): void {
  const sheet = new CSSStyleSheet();
  sheet.replaceSync(PANEL_CSS);
  shadow.adoptedStyleSheets = [sheet];
}
