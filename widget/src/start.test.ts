import {describe, expect, it} from 'vitest';
import {start} from './start';

describe('start', () => {
  it('leaves the page as it was (nothing to show until #152)', () => {
    document.body.innerHTML = '<h1 class="article-headline">Rubrik</h1>';

    start();

    expect(document.body.innerHTML).toBe(
      '<h1 class="article-headline">Rubrik</h1>'
    );
  });
});
