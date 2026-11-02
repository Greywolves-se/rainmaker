/**
 * Where the next step goes (#220, ADR 0005): beside the article when the publisher's layout puts
 * the sidebar place there (desktop), otherwise after the article text (mobile). The layout
 * decides, not a screen width, so the publisher's own breakpoints are followed.
 *
 * @param sidebar the place beside the article, if the page has one
 * @param afterArticle the place after the article text, if the page has one
 * @param articleEnd the end of the article content, which tells where the article text is
 * @returns the place to put the next step in, or null when the page has none
 */
export function choosePlace(
  sidebar: Element | null,
  afterArticle: Element | null,
  articleEnd: Element
): Element | null {
  if (sidebar && isBesideArticle(sidebar, articleEnd)) {
    return sidebar;
  }
  // Below the article, the sidebar place still covers nothing: it is in the page's flow.
  return afterArticle ?? sidebar;
}

/**
 * Whether the place's column is laid out beside the article text: it has a width and doesn't
 * overlap the article horizontally. An empty place may be hidden (the test site's
 * `:empty { display: none }`), so its nearest ancestor with a width stands for it.
 */
function isBesideArticle(place: Element, articleEnd: Element): boolean {
  const column = nearestBox(place);
  if (!column) {
    return false;
  }
  const article = articleEnd.getBoundingClientRect();
  return column.left >= article.right || column.right <= article.left;
}

function nearestBox(element: Element): DOMRect | null {
  for (
    let current: Element | null = element;
    current && current !== document.body;
    current = current.parentElement
  ) {
    const box = current.getBoundingClientRect();
    if (box.width > 0) {
      return box;
    }
  }
  return null;
}
