import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {OPEN_DELAY_MS} from './hover-panel';
import {start} from './start';

const ARTICLE_URL =
  'http://localhost:3000/artikel/badrumsrenovering-experternas-rad';
const WIDGET_URL = 'http://localhost:8081/widget/rainmaker.js';
const ARTICLE_PAGE =
  `<link rel="canonical" href="${ARTICLE_URL}">` +
  '<h1 class="article__headline" data-rainmaker-headline>Experternas råd</h1>' +
  '<p>Brödtext.</p>';

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

function widgetScript(): HTMLScriptElement {
  const script = document.createElement('script');
  script.src = WIDGET_URL;
  return script;
}

function headline(): HTMLElement {
  return document.querySelector<HTMLElement>('[data-rainmaker-headline]')!;
}

function panel(): ShadowRoot | null {
  return document.querySelector('[data-rainmaker-panel]')?.shadowRoot ?? null;
}

function answerWith(status: number, body: unknown): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(body), {status}))
  );
}

async function hover(milliseconds: number): Promise<void> {
  headline().dispatchEvent(new MouseEvent('mouseenter'));
  await vi.advanceTimersByTimeAsync(milliseconds);
}

describe('rainmaker.js on an article page', () => {
  const uncaughtErrors: unknown[] = [];
  const recordError = (event: ErrorEvent) => uncaughtErrors.push(event.error);

  beforeEach(() => {
    vi.useFakeTimers();
    document.documentElement.lang = 'sv';
    document.head.innerHTML = '';
    document.body.innerHTML = ARTICLE_PAGE;
    uncaughtErrors.length = 0;
    window.addEventListener('error', recordError);
  });

  afterEach(() => {
    window.removeEventListener('error', recordError);
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('shows the approved links and the "Annons" label when the reader hovers the headline', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await hover(OPEN_DELAY_MS);

    const shown = panel();
    expect(shown?.textContent).toContain('Annons');
    const links = [...(shown?.querySelectorAll('a') ?? [])];
    expect(links.map(link => link.getAttribute('href'))).toEqual([
      'http://localhost:8081/c/first',
      'http://localhost:8081/c/second',
    ]);
    expect(links[0]?.textContent).toContain(
      'Gratis hembesök för badrumsoffert'
    );
    expect(fetch).toHaveBeenCalledWith(
      `http://localhost:8081/widget/v1/links?url=${encodeURIComponent(ARTICLE_URL)}`,
      expect.objectContaining({credentials: 'omit'})
    );
  });

  it("keeps the panel out of reach of the page's rules for ordinary elements", async () => {
    answerWith(200, APPROVED_LINKS);
    const pageStyle = document.createElement('style');
    pageStyle.textContent = 'div { display: none; padding: 20px; }';
    document.head.append(pageStyle);
    start(widgetScript());

    await hover(OPEN_DELAY_MS);

    const host = document.querySelector<HTMLElement>('[data-rainmaker-panel]')!;
    expect(host.localName).toBe('rainmaker-panel');
    expect(getComputedStyle(host).display).not.toBe('none');
  });

  it('closes the panel when the pointer leaves the headline', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());
    await hover(OPEN_DELAY_MS);

    headline().dispatchEvent(new MouseEvent('mouseleave'));
    await vi.advanceTimersByTimeAsync(1000);

    expect(panel()).toBeNull();
  });

  it('keeps the panel open while the pointer moves from the headline into it', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());
    await hover(OPEN_DELAY_MS);

    headline().dispatchEvent(new MouseEvent('mouseleave'));
    document
      .querySelector('[data-rainmaker-panel]')!
      .dispatchEvent(new MouseEvent('mouseenter'));
    await vi.advanceTimersByTimeAsync(1000);

    expect(panel()).not.toBeNull();
  });

  it.each([
    ['red, unknown or not approved', 200, {articleUrl: ARTICLE_URL, links: []}],
    ['a bad request', 400, {}],
    ['G3 unavailable', 503, {}],
  ])(
    'shows nothing on hover for an article without links (%s)',
    async (_case, status, body) => {
      answerWith(status, body);
      const pageBefore = document.body.innerHTML;
      start(widgetScript());

      await hover(OPEN_DELAY_MS);

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

    await hover(OPEN_DELAY_MS);

    expect(document.body.innerHTML).toBe(pageBefore);
    expect(uncaughtErrors).toEqual([]);
  });

  it('gives up without a panel when the API does not answer in time', async () => {
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

    await hover(10_000);

    expect(document.body.innerHTML).toBe(pageBefore);
    expect(uncaughtErrors).toEqual([]);
  });

  it('shows no panel when the pointer leaves before a slow answer arrives', async () => {
    let answer: (response: Response) => void = () => {};
    vi.stubGlobal(
      'fetch',
      vi.fn(
        () =>
          new Promise<Response>(resolve => {
            answer = resolve;
          })
      )
    );
    start(widgetScript());

    await hover(OPEN_DELAY_MS + 100);
    headline().dispatchEvent(new MouseEvent('mouseleave'));
    answer(new Response(JSON.stringify(APPROVED_LINKS), {status: 200}));
    // Look before the 200 ms close timer could hide a panel that wrongly opened.
    await vi.advanceTimersByTimeAsync(10);
    expect(panel()).toBeNull();

    await vi.advanceTimersByTimeAsync(1000);
    expect(panel()).toBeNull();
    expect(uncaughtErrors).toEqual([]);
  });

  it('does not open the panel on a short hover', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await hover(OPEN_DELAY_MS - 1);
    headline().dispatchEvent(new MouseEvent('mouseleave'));
    await vi.advanceTimersByTimeAsync(1000);

    expect(panel()).toBeNull();
  });

  it('does nothing on a page without a headline', async () => {
    answerWith(200, APPROVED_LINKS);
    document.body.innerHTML = '<h1>Förstasidan</h1>';

    start(widgetScript());

    expect(fetch).not.toHaveBeenCalled();
  });

  it('asks for the links once per page, however often the reader hovers', async () => {
    answerWith(200, APPROVED_LINKS);
    start(widgetScript());

    await hover(OPEN_DELAY_MS);
    headline().dispatchEvent(new MouseEvent('mouseleave'));
    await vi.advanceTimersByTimeAsync(1000);
    await hover(OPEN_DELAY_MS);

    expect(fetch).toHaveBeenCalledOnce();
    expect(panel()).not.toBeNull();
  });

  it.each([
    [
      'the API was down',
      () => Promise.reject(new TypeError('Failed to fetch')),
    ],
    ['G3 was unavailable', async () => new Response('{}', {status: 503})],
  ])(
    'asks again on the next hover when the first call failed (%s)',
    async (_case, firstAnswer) => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockImplementationOnce(firstAnswer)
          .mockImplementation(
            async () =>
              new Response(JSON.stringify(APPROVED_LINKS), {status: 200})
          )
      );
      start(widgetScript());

      await hover(OPEN_DELAY_MS);
      expect(panel()).toBeNull();
      headline().dispatchEvent(new MouseEvent('mouseleave'));
      await vi.advanceTimersByTimeAsync(1000);
      await hover(OPEN_DELAY_MS);

      expect(fetch).toHaveBeenCalledTimes(2);
      expect(panel()).not.toBeNull();
    }
  );

  it('does not ask again when the article simply has no links', async () => {
    answerWith(200, {articleUrl: ARTICLE_URL, links: []});
    start(widgetScript());

    await hover(OPEN_DELAY_MS);
    headline().dispatchEvent(new MouseEvent('mouseleave'));
    await vi.advanceTimersByTimeAsync(1000);
    await hover(OPEN_DELAY_MS);

    expect(fetch).toHaveBeenCalledOnce();
  });

  it('waits for the headline when it starts before the page has loaded (script in <head>)', async () => {
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

    await hover(OPEN_DELAY_MS);

    expect(panel()).not.toBeNull();
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
    document.head.innerHTML = '';
    document.body.innerHTML = ARTICLE_PAGE;
    uncaughtErrors.length = 0;
    window.addEventListener('error', recordError);
  });

  afterEach(() => {
    window.removeEventListener('error', recordError);
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.resetModules();
    vi.doUnmock('./links-api');
    vi.doUnmock('./panel');
  });

  it('throws no uncaught error when the hover handler fails', async () => {
    vi.doMock('./links-api', () => ({
      fetchApprovedLinks: () => {
        throw new Error('bug in the hover handler');
      },
    }));
    const {start: startWithBug} = await import('./start');
    startWithBug(widgetScript());

    await hover(OPEN_DELAY_MS);

    expect(uncaughtErrors).toEqual([]);
    expect(panel()).toBeNull();
  });

  it('throws no uncaught error when building the panel fails', async () => {
    vi.doMock('./panel', () => ({
      createPanel: () => {
        throw new Error('bug in the panel');
      },
    }));
    const {start: startWithBug} = await import('./start');
    startWithBug(widgetScript());

    await hover(OPEN_DELAY_MS);

    expect(uncaughtErrors).toEqual([]);
    expect(panel()).toBeNull();
  });
});
