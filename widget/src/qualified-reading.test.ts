import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {IDLE_AFTER_MS, watchQualifiedReading} from './qualified-reading';

let visibility: DocumentVisibilityState = 'visible';

function setVisibility(state: DocumentVisibilityState): void {
  visibility = state;
  document.dispatchEvent(new Event('visibilitychange'));
}

function scroll(): void {
  window.dispatchEvent(new Event('scroll'));
}

/** Reads for the given time, one second at a time, scrolling every `scrollEvery` seconds. */
async function read(seconds: number, scrollEvery = 10): Promise<void> {
  for (let second = 1; second <= seconds; second++) {
    if (scrollEvery > 0 && second % scrollEvery === 0) {
      scroll();
    }
    await vi.advanceTimersByTimeAsync(1000);
  }
}

describe('qualified reading', () => {
  const stops: Array<() => void> = [];

  function watch(seconds = 60): ReturnType<typeof vi.fn> {
    const onQualified = vi.fn();
    stops.push(watchQualifiedReading(seconds, onQualified));
    return onQualified;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    visibility = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(
      () => visibility
    );
  });

  afterEach(() => {
    stops.splice(0).forEach(stop => stop());
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('is qualified after 60 s of visible, active reading with scrolling', async () => {
    const onQualified = watch();

    await read(59);
    expect(onQualified).not.toHaveBeenCalled();

    await read(1);
    expect(onQualified).toHaveBeenCalledOnce();
  });

  it('does not count the time the page is hidden', async () => {
    const onQualified = watch();

    await read(30);
    setVisibility('hidden');
    await read(40);
    setVisibility('visible');
    await read(29);
    expect(onQualified).not.toHaveBeenCalled();

    await read(1);
    expect(onQualified).toHaveBeenCalledOnce();
  });

  it('is never qualified without scrolling', async () => {
    const onQualified = watch();

    for (let second = 0; second < 180; second++) {
      // Active, but only with the keyboard and the pointer.
      window.dispatchEvent(new Event(second % 2 ? 'keydown' : 'pointermove'));
      await vi.advanceTimersByTimeAsync(1000);
    }

    expect(onQualified).not.toHaveBeenCalled();
  });

  it('stops counting when the reader is idle', async () => {
    const onQualified = watch();

    // One scroll, then a phone left on the table: only the first 30 s count.
    scroll();
    await read(300, 0);
    expect(onQualified).not.toHaveBeenCalled();

    // Active again: 30 s counted so far, so 30 more are needed.
    scroll();
    await read(29);
    expect(onQualified).not.toHaveBeenCalled();
    await read(1);
    expect(onQualified).toHaveBeenCalledOnce();
    expect(IDLE_AFTER_MS).toBe(30_000);
  });

  it('reports only once per page view', async () => {
    const onQualified = watch();

    await read(200);

    expect(onQualified).toHaveBeenCalledOnce();
  });

  it('uses the threshold it is given', async () => {
    const onQualified = watch(10);

    await read(10, 5);

    expect(onQualified).toHaveBeenCalledOnce();
  });

  it('stores and sends nothing', async () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    const cookiesBefore = document.cookie;
    window.localStorage.clear();
    window.sessionStorage.clear();

    watch();
    await read(120);

    expect(fetch).not.toHaveBeenCalled();
    expect(document.cookie).toBe(cookiesBefore);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('never lets an error in the callback reach the page (fail open)', async () => {
    const uncaught: unknown[] = [];
    const onError = (event: ErrorEvent) => uncaught.push(event.error);
    window.addEventListener('error', onError);
    stops.push(
      watchQualifiedReading(10, () => {
        throw new Error('widget bug');
      })
    );

    await read(20, 5);

    window.removeEventListener('error', onError);
    expect(uncaught).toEqual([]);
  });

  it('stops watching when stopped', async () => {
    const onQualified = vi.fn();
    const stop = watchQualifiedReading(10, onQualified);

    stop();
    await read(60, 5);

    expect(onQualified).not.toHaveBeenCalled();
  });
});
