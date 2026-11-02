import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {choosePlace} from './next-step-place';

/** A box as getBoundingClientRect reports it; only the horizontal part matters here. */
function box(left: number, width: number): DOMRect {
  return {left, width, right: left + width} as DOMRect;
}

/**
 * Lays out the test site's article page (#217). jsdom has no layout, so each element reports the
 * box given here, and every other element an empty one.
 */
function layOut(boxes: Record<string, DOMRect>): void {
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(
    function (this: Element) {
      const match = Object.keys(boxes).find(selector => this.matches(selector));
      return match ? boxes[match]! : box(0, 0);
    }
  );
}

describe('where the next step goes', () => {
  let sidebar: Element;
  let afterArticle: Element;
  let articleEnd: Element;

  beforeEach(() => {
    document.body.innerHTML =
      '<div class="article-layout">' +
      '<article><div class="article__body"><p>Text.</p>' +
      '<div data-rainmaker-article-end></div></div>' +
      '<div data-rainmaker-next-step="after-article"></div></article>' +
      '<aside class="article-sidebar">' +
      '<div data-rainmaker-next-step="sidebar"></div></aside>' +
      '</div>';
    sidebar = document.querySelector('[data-rainmaker-next-step="sidebar"]')!;
    afterArticle = document.querySelector(
      '[data-rainmaker-next-step="after-article"]'
    )!;
    articleEnd = document.querySelector('[data-rainmaker-article-end]')!;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('goes beside the article when the sidebar is a column of its own (desktop)', () => {
    layOut({
      '.article-layout': box(0, 1020),
      '[data-rainmaker-article-end]': box(0, 680),
      '.article-sidebar': box(720, 300),
    });

    expect(choosePlace(sidebar, afterArticle, articleEnd)).toBe(sidebar);
  });

  it('measures the place itself once it has a width of its own', () => {
    layOut({
      '[data-rainmaker-article-end]': box(0, 680),
      '[data-rainmaker-next-step="sidebar"]': box(720, 300),
    });

    expect(choosePlace(sidebar, afterArticle, articleEnd)).toBe(sidebar);
  });

  it('goes after the article when the sidebar is below it (mobile)', () => {
    layOut({
      '.article-layout': box(0, 390),
      '[data-rainmaker-article-end]': box(0, 390),
      '.article-sidebar': box(0, 390),
    });

    expect(choosePlace(sidebar, afterArticle, articleEnd)).toBe(afterArticle);
  });

  it('goes after the article when the sidebar is hidden', () => {
    layOut({
      '.article-layout': box(0, 390),
      '[data-rainmaker-article-end]': box(0, 390),
    });

    expect(choosePlace(sidebar, afterArticle, articleEnd)).toBe(afterArticle);
  });

  it('goes after the article on a page without a place beside it', () => {
    layOut({'[data-rainmaker-article-end]': box(0, 680)});

    expect(choosePlace(null, afterArticle, articleEnd)).toBe(afterArticle);
  });

  it('uses the sidebar place below the article when it is the only place', () => {
    layOut({
      '.article-layout': box(0, 390),
      '[data-rainmaker-article-end]': box(0, 390),
      '.article-sidebar': box(0, 390),
    });

    expect(choosePlace(sidebar, null, articleEnd)).toBe(sidebar);
  });

  it('has no place on a page without places', () => {
    expect(choosePlace(null, null, articleEnd)).toBeNull();
  });
});
