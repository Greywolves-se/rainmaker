import {afterEach, describe, expect, it, vi} from 'vitest';

describe('rainmaker.js entry point', () => {
  afterEach(() => {
    vi.resetModules();
    vi.doUnmock('./start');
  });

  it('starts the widget when the script loads', async () => {
    const start = vi.fn();
    vi.doMock('./start', () => ({start}));

    await import('./main');

    expect(start).toHaveBeenCalledOnce();
  });

  it('never lets an error reach the publisher page (fail open)', async () => {
    vi.doMock('./start', () => ({
      start: () => {
        throw new Error('widget bug');
      },
    }));

    await expect(import('./main')).resolves.toBeDefined();
  });
});
