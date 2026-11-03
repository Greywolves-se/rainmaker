import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {watchLeavingArticle} from './leaving-article';

/** Stands in for the browser's IntersectionObserver, which jsdom doesn't have. */
class FakeIntersectionObserver {
  static latest: FakeIntersectionObserver | undefined;
  readonly observed: Element[] = [];
  disconnected = false;

  constructor(
    private readonly callback: IntersectionObserverCallback,
    readonly options: IntersectionObserverInit = {}
  ) {
    FakeIntersectionObserver.latest = this;
  }

  observe(target: Element): void {
    this.observed.push(target);
  }

  disconnect(): void {
    this.disconnected = true;
  }

  /** What the browser reports when the marker enters or leaves the observed area. */
  report(isIntersecting: boolean): void {
    if (this.disconnected) {
      return;
    }
    const entry = {isIntersecting, target: this.observed[0]};
    this.callback(
      [entry as IntersectionObserverEntry],
      this as unknown as IntersectionObserver
    );
  }
}

/** The reader reaches the end of the article, or scrolls back up above it. */
function articleEnd(reached: boolean): void {
  FakeIntersectionObserver.latest?.report(reached);
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

describe('leaving the article', () => {
  const stops: Array<() => void> = [];
  let marker: HTMLElement;

  function watch(seconds = 10): ReturnType<typeof vi.fn> {
    const onLeaving = vi.fn();
    stops.push(watchLeavingArticle(marker, seconds, onLeaving));
    return onLeaving;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    FakeIntersectionObserver.latest = undefined;
    marker = document.createElement('div');
    document.body.append(marker);
  });

  afterEach(() => {
    stops.splice(0).forEach(stop => stop());
    marker.remove();
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('signals when the reader reaches the end after qualified reading', async () => {
    const onLeaving = watch();

    await read(10);
    expect(onLeaving).not.toHaveBeenCalled();

    articleEnd(true);
    expect(onLeaving).toHaveBeenCalledOnce();
  });

  it('signals at qualified reading when the reader is already at the end', async () => {
    const onLeaving = watch();

    articleEnd(true);
    await read(9);
    expect(onLeaving).not.toHaveBeenCalled();

    await read(1);
    expect(onLeaving).toHaveBeenCalledOnce();
  });

  it('never signals before qualified reading', async () => {
    const onLeaving = watch();

    articleEnd(true);
    await read(9);

    expect(onLeaving).not.toHaveBeenCalled();
  });

  it('waits after qualified reading until the reader reaches the end', async () => {
    const onLeaving = watch();

    await read(60);

    expect(onLeaving).not.toHaveBeenCalled();
  });

  it('waits when the reader scrolled back up above the end before qualifying', async () => {
    const onLeaving = watch();

    articleEnd(true);
    articleEnd(false);
    await read(10);
    expect(onLeaving).not.toHaveBeenCalled();

    articleEnd(true);
    expect(onLeaving).toHaveBeenCalledOnce();
  });

  it('signals only once per page view', async () => {
    const onLeaving = watch();

    await read(10);
    articleEnd(true);
    articleEnd(false);
    articleEnd(true);
    await read(30);

    expect(onLeaving).toHaveBeenCalledOnce();
    expect(FakeIntersectionObserver.latest?.disconnected).toBe(true);
  });

  it('counts the end as reached on screen and anywhere above it', () => {
    watch();

    const observer = FakeIntersectionObserver.latest;
    expect(observer?.observed).toEqual([marker]);
    // The observed area reaches far above the screen: a marker the reader scrolled past, or
    // jumped past with the End key, still counts as reached.
    const [top, ...rest] = observer?.options.rootMargin?.split(' ') ?? [];
    expect(parseInt(top ?? '')).toBeGreaterThanOrEqual(1_000_000);
    expect(rest).toEqual(['0px', '0px', '0px']);
  });

  it('never signals, and throws nothing, without IntersectionObserver', async () => {
    vi.stubGlobal('IntersectionObserver', undefined);
    const onLeaving = watch();

    await read(60);

    expect(onLeaving).not.toHaveBeenCalled();
  });

  it('stores and sends nothing', async () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    const cookiesBefore = document.cookie;
    window.localStorage.clear();
    window.sessionStorage.clear();

    watch();
    await read(10);
    articleEnd(true);

    expect(fetch).not.toHaveBeenCalled();
    expect(document.cookie).toBe(cookiesBefore);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('never lets an error in the callback reach the page (fail open)', async () => {
    const throwing = () => {
      throw new Error('widget bug');
    };

    stops.push(watchLeavingArticle(marker, 10, throwing));
    await read(10);
    expect(() => articleEnd(true)).not.toThrow();

    stops.push(watchLeavingArticle(marker, 10, throwing));
    articleEnd(true);
    const uncaught: unknown[] = [];
    const onError = (event: ErrorEvent) => uncaught.push(event.error);
    window.addEventListener('error', onError);
    await read(10);
    window.removeEventListener('error', onError);
    expect(uncaught).toEqual([]);
  });

  it('stops watching when stopped', async () => {
    const onLeaving = vi.fn();
    const stop = watchLeavingArticle(marker, 10, onLeaving);

    stop();
    articleEnd(true);
    await read(30);

    expect(onLeaving).not.toHaveBeenCalled();
  });
});
