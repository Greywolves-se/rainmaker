import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {start} from './start';

const ARTICLE_URL =
  'http://localhost:3000/artikel/badrumsrenovering-experternas-rad';
const WIDGET_URL = 'http://localhost:8081/widget/rainmaker.js';

/** The test site's article page (#217): the end marker and the two places for the next step. */
const ARTICLE_PAGE =
  `<link rel="canonical" href="${ARTICLE_URL}">` +
  '<div class="article-layout">' +
  '<article><div class="article__body"><p>Brödtext.</p>' +
  '<div data-rainmaker-article-end aria-hidden="true"></div></div>' +
  '<div data-rainmaker-next-step="after-article"></div></article>' +
  '<aside class="article-sidebar"><section>Läs också</section>' +
  '<div data-rainmaker-next-step="sidebar"></div></aside>' +
  '</div>';

const SIDEBAR_PLACE = '[data-rainmaker-next-step="sidebar"]';
const AFTER_ARTICLE_PLACE = '[data-rainmaker-next-step="after-article"]';

const APPROVED_LINKS = {
  articleUrl: ARTICLE_URL,
  links: [
    {
      offerId: 'offer-1',
      advertiser: 'Vintra Kakel & Bad',
      title: 'Gratis hembesök för badrumsoffert',
      text: 'Vi mäter upp och ger fast pris inom en vecka.',
      clickUrl: 'http://localhost:8081/c/first',
    },
    {
      offerId: 'offer-2',
      advertiser: 'Vintra Rör',
      title: 'Ny dusch på en dag',
      text: 'Fast pris, inklusive montering.',
      clickUrl: 'http://localhost:8081/c/second',
    },
  ],
};

/**
 * Stands in for the browser's IntersectionObserver, which jsdom doesn't have. The widget observes
 * one element, the end-of-article marker.
 */
class FakeIntersectionObserver {
  static latest: FakeIntersectionObserver | undefined;
  /** Every observer made, since the browser tells each of them. */
  static readonly all: FakeIntersectionObserver[] = [];
  private disconnected = false;

  constructor(private readonly callback: IntersectionObserverCallback) {
    FakeIntersectionObserver.latest = this;
    FakeIntersectionObserver.all.push(this);
  }

  observe(): void {}

  disconnect(): void {
    this.disconnected = true;
  }

  report(isIntersecting: boolean): void {
    if (!this.disconnected) {
      this.callback(
        [{isIntersecting} as IntersectionObserverEntry],
        this as unknown as IntersectionObserver
      );
    }
  }
}

/**
 * Stands in for a constructed stylesheet, which every browser the widget runs in has (Chrome 73,
 * Firefox 101, Safari 16.4) but jsdom doesn't. It keeps the CSS, so a test can read it.
 */
class FakeCSSStyleSheet {
  cssText = '';

  replaceSync(text: string): void {
    this.cssText = text;
  }
}

/** A browser with constructed stylesheets, as the widget needs (#187). */
function supportConstructedStylesheets(): void {
  vi.stubGlobal('CSSStyleSheet', FakeCSSStyleSheet);
  Object.defineProperty(ShadowRoot.prototype, 'adoptedStyleSheets', {
    configurable: true,
    writable: true,
    value: [],
  });
}

/** Back to jsdom, which has no constructed stylesheets, like Safari before 16.4. */
function removeConstructedStylesheets(): void {
  Reflect.deleteProperty(ShadowRoot.prototype, 'adoptedStyleSheets');
}

/** Each test is a new page view: no copy of the widget has started on it yet. */
function newPageView(): void {
  document.documentElement.removeAttribute('data-rainmaker-started');
  FakeIntersectionObserver.latest = undefined;
  FakeIntersectionObserver.all.length = 0;
  supportConstructedStylesheets();
}

/** A box as getBoundingClientRect reports it; only the horizontal part matters here. */
function box(left: number, width: number): DOMRect {
  return {left, width, right: left + width} as DOMRect;
}

/** The test site's layout at desktop width (sidebar beside the article) or at 390 px (below it). */
function layOut(width: 'desktop' | 'mobile'): void {
  const boxes: Record<string, DOMRect> =
    width === 'desktop'
      ? {
          '[data-rainmaker-article-end]': box(0, 680),
          '.article-sidebar': box(720, 300),
        }
      : {
          '[data-rainmaker-article-end]': box(0, 390),
          '.article-sidebar': box(0, 390),
        };
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(
    function (this: Element) {
      const match = Object.keys(boxes).find(selector => this.matches(selector));
      return match ? boxes[match]! : box(0, 0);
    }
  );
}

function widgetScript(qualifiedReadingSeconds = 60): HTMLScriptElement {
  const script = document.createElement('script');
  script.src = WIDGET_URL;
  script.dataset.qualifiedReadingSeconds = String(qualifiedReadingSeconds);
  return script;
}

/** The next step in the given place, or anywhere on the page. */
function nextStep(place = 'body'): ShadowRoot | null {
  return (
    document.querySelector(`${place} [data-rainmaker-panel]`)?.shadowRoot ??
    null
  );
}

function answerWith(status: number, body: unknown): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(body), {status}))
  );
}

/** Reads actively for the given time, scrolling every 5 seconds. */
async function read(seconds: number): Promise<void> {
  for (let second = 1; second <= seconds; second++) {
    if (second % 5 === 0) {
      window.dispatchEvent(new Event('scroll'));
    }
    await vi.advanceTimersByTimeAsync(1000);
  }
}

/** The reader reaches the end of the article, or scrolls back up above it. */
async function articleEnd(reached: boolean): Promise<void> {
  FakeIntersectionObserver.all.forEach(observer => observer.report(reached));
  // Lets the widget's call to the API answer and the next step be added.
  await vi.advanceTimersByTimeAsync(0);
}

/** What the widget needs before it shows anything: qualified reading, then the article's end. */
async function readToTheEnd(): Promise<void> {
  await read(60);
  await articleEnd(true);
}

describe('rainmaker.js on an article page', () => {
  const uncaughtErrors: unknown[] = [];
  const recordError = (event: ErrorEvent) => uncaughtErrors.push(event.error);

  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    newPageView();
    document.documentElement.lang = 'sv';
    document.head.innerHTML = '';
    document.body.innerHTML = ARTICLE_PAGE;
    layOut('desktop');
    uncaughtErrors.length = 0;
    window.addEventListener('error', recordError);
  });

  afterEach(() => {
    window.removeEventListener('error', recordError);
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    removeConstructedStylesheets();
  });

  it('shows the approved links, labelled "Annons", beside the article after the reader has read it (desktop)', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await readToTheEnd();

    const shown = nextStep(SIDEBAR_PLACE);
    expect(shown?.textContent).toContain('Annons');
    const links = [...(shown?.querySelectorAll('a') ?? [])];
    expect(links.map(link => link.getAttribute('href'))).toEqual([
      'http://localhost:8081/c/first',
      'http://localhost:8081/c/second',
    ]);
    expect(links[0]?.rel).toBe('sponsored noopener');
    expect(links[0]?.textContent).toContain(
      'Gratis hembesök för badrumsoffert'
    );
    expect(nextStep(AFTER_ARTICLE_PLACE)).toBeNull();
    expect(fetch).toHaveBeenCalledWith(
      `http://localhost:8081/widget/v1/links?url=${encodeURIComponent(ARTICLE_URL)}`,
      expect.objectContaining({credentials: 'omit'})
    );
  });

  it('shows the links after the article text when the sidebar is below it (mobile)', async () => {
    layOut('mobile');
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await readToTheEnd();

    expect(nextStep(AFTER_ARTICLE_PLACE)?.textContent).toContain('Annons');
    expect(nextStep(SIDEBAR_PLACE)).toBeNull();
  });

  it('shows nothing and asks for nothing while the reader is still reading', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await read(59);
    await articleEnd(true);

    expect(nextStep()).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('shows nothing to a reader who read long enough but stopped before the end', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await read(120);

    expect(nextStep()).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('shows the links when a reader who reached the end early has read long enough', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await articleEnd(true);
    await read(60);

    expect(nextStep(SIDEBAR_PLACE)).not.toBeNull();
  });

  it('uses the qualified reading threshold from the script tag', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript(5));

    await read(5);
    await articleEnd(true);

    expect(nextStep(SIDEBAR_PLACE)).not.toBeNull();
  });

  it('asks for the links once per page view, even when the reader comes back to the end', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());
    await readToTheEnd();

    await articleEnd(false);
    await articleEnd(true);
    await read(60);

    expect(fetch).toHaveBeenCalledOnce();
    expect(document.querySelectorAll('[data-rainmaker-panel]')).toHaveLength(1);
  });

  it.each([
    ['red, unknown or not approved', 200, {articleUrl: ARTICLE_URL, links: []}],
    ['a bad request', 400, {}],
    ['G3 unavailable', 503, {}],
  ])(
    'shows nothing for an article without links (%s)',
    async (_case, status, body) => {
      answerWith(status, body);
      const pageBefore = document.body.innerHTML;
      start(widgetScript());

      await readToTheEnd();

      expect(document.body.innerHTML).toBe(pageBefore);
    }
  );

  it('leaves the page working, with no uncaught error, when the API is down', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      })
    );
    const pageBefore = document.body.innerHTML;
    start(widgetScript());

    await readToTheEnd();

    expect(document.body.innerHTML).toBe(pageBefore);
    expect(uncaughtErrors).toEqual([]);
  });

  it('gives up without showing anything when the API does not answer in time', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url: string, init: RequestInit) =>
          new Promise((_resolve, reject) =>
            init.signal?.addEventListener('abort', () =>
              reject(init.signal?.reason)
            )
          )
      )
    );
    const pageBefore = document.body.innerHTML;
    start(widgetScript());

    await readToTheEnd();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(document.body.innerHTML).toBe(pageBefore);
    expect(uncaughtErrors).toEqual([]);
  });

  it('starts once when the script tag is on the page twice (template and tag manager)', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());
    start(widgetScript());

    await readToTheEnd();

    expect(fetch).toHaveBeenCalledOnce();
    expect(document.querySelectorAll('[data-rainmaker-panel]')).toHaveLength(1);
  });

  it('shows nothing and asks for nothing in a browser without constructed stylesheets', async () => {
    removeConstructedStylesheets();
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await readToTheEnd();

    expect(fetch).not.toHaveBeenCalled();
    expect(nextStep()).toBeNull();
    expect(FakeIntersectionObserver.latest).toBeUndefined();
  });

  it('puts the next step in the page flow, so it can never cover the article', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await readToTheEnd();

    const host = document.querySelector(
      `${SIDEBAR_PLACE} > [data-rainmaker-panel]`
    );
    expect(host?.getAttribute('style')).toBeNull();
    const css = (host?.shadowRoot?.adoptedStyleSheets ?? [])
      .map(sheet => (sheet as unknown as FakeCSSStyleSheet).cssText)
      .join('');
    expect(css).toContain('display: block');
    expect(css).not.toMatch(/position|z-index/);
  });

  it('does nothing on a page without an end-of-article marker', async () => {
    answerWith(200, APPROVED_LINKS);
    document.body.innerHTML = '<h1>Förstasidan</h1>';
    start(widgetScript());

    await read(120);

    expect(FakeIntersectionObserver.latest).toBeUndefined();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('does nothing on a page without a place for the next step', async () => {
    answerWith(200, APPROVED_LINKS);
    document.body.innerHTML =
      '<article><p>Text.</p><div data-rainmaker-article-end></div></article>';
    start(widgetScript());

    await read(120);

    expect(FakeIntersectionObserver.latest).toBeUndefined();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("uses the publisher's own places when the script tag names them", async () => {
    answerWith(200, APPROVED_LINKS);
    document.body.innerHTML =
      '<main><p>Text.</p><hr class="story-end"></main>' +
      '<div class="ad-rail"><div id="beside"></div></div>' +
      '<div id="after"></div>';
    // Replaces the test site's layout from beforeEach.
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(
      function (this: Element) {
        if (this.matches('.story-end')) return box(0, 680);
        if (this.matches('.ad-rail')) return box(720, 300);
        return box(0, 0);
      }
    );
    const script = widgetScript();
    script.dataset.articleEndSelector = '.story-end';
    script.dataset.sidebarPlaceSelector = '#beside';
    script.dataset.afterArticlePlaceSelector = '#after';
    start(script);

    await readToTheEnd();

    expect(nextStep('#beside')).not.toBeNull();
  });

  it('waits for the article when it starts before the page has loaded (script in <head>)', async () => {
    answerWith(200, APPROVED_LINKS);
    document.body.innerHTML = '';
    Object.defineProperty(document, 'readyState', {
      value: 'loading',
      configurable: true,
    });
    try {
      start(widgetScript());
      document.body.innerHTML = ARTICLE_PAGE;
      document.dispatchEvent(new Event('DOMContentLoaded'));
    } finally {
      // Removes the override, so document.readyState is jsdom's own again.
      delete (document as {readyState?: string}).readyState;
    }

    await readToTheEnd();

    expect(nextStep(SIDEBAR_PLACE)).not.toBeNull();
  });
});

// A thrown error in a handler shows up as an 'error' event on window (checked below). A rejected
// promise that nobody catches makes Vitest fail the whole run ("Unhandled Rejection").
describe('rainmaker.js with a bug in its own code', () => {
  const uncaughtErrors: unknown[] = [];
  const recordError = (event: ErrorEvent) => uncaughtErrors.push(event.error);

  beforeEach(() => {
    // start is already imported above; without this, import('./start') returns that copy,
    // which uses the real modules instead of the mocks.
    vi.resetModules();
    answerWith(200, APPROVED_LINKS);
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    newPageView();
    document.head.innerHTML = '';
    document.body.innerHTML = ARTICLE_PAGE;
    layOut('desktop');
    uncaughtErrors.length = 0;
    window.addEventListener('error', recordError);
  });

  afterEach(() => {
    window.removeEventListener('error', recordError);
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    removeConstructedStylesheets();
    vi.resetModules();
    vi.doUnmock('./links-api');
    vi.doUnmock('./panel');
  });

  it('throws no uncaught error when asking for the links fails', async () => {
    vi.doMock('./links-api', () => ({
      fetchApprovedLinks: () => {
        throw new Error('bug in the call');
      },
    }));
    const {start: startWithBug} = await import('./start');
    startWithBug(widgetScript());

    await readToTheEnd();

    expect(uncaughtErrors).toEqual([]);
    expect(nextStep()).toBeNull();
  });

  it('throws no uncaught error when building the next step fails', async () => {
    vi.doMock('./panel', async importOriginal => ({
      ...(await importOriginal<typeof import('./panel')>()),
      createPanel: () => {
        throw new Error('bug in the panel');
      },
    }));
    const {start: startWithBug} = await import('./start');
    startWithBug(widgetScript());

    await readToTheEnd();

    expect(uncaughtErrors).toEqual([]);
    expect(nextStep()).toBeNull();
  });
});
