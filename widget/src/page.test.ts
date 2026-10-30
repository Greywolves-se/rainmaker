import {afterEach, describe, expect, it} from 'vitest';
import {articleUrl, readSettings} from './page';

describe('articleUrl', () => {
  afterEach(() => {
    document.head.innerHTML = '';
  });

  it('uses the canonical link exactly as the page gives it', () => {
    document.head.innerHTML =
      '<link rel="canonical" href="http://localhost:3000/artikel/räd-och-tips">';

    expect(articleUrl(document)).toBe(
      'http://localhost:3000/artikel/räd-och-tips'
    );
  });

  it('falls back to the address without query string or fragment', () => {
    window.history.replaceState(
      null,
      '',
      '/artikel/utan-canonical?utm_source=x#kommentarer'
    );

    expect(articleUrl(document)).toBe(
      `${window.location.origin}/artikel/utan-canonical`
    );
  });
});

describe('readSettings', () => {
  function script(src: string, headlineSelector?: string): HTMLScriptElement {
    const element = document.createElement('script');
    element.src = src;
    if (headlineSelector) {
      element.dataset.headlineSelector = headlineSelector;
    }
    return element;
  }

  it('calls the API on the origin the script was loaded from', () => {
    expect(
      readSettings(script('http://localhost:8081/widget/rainmaker.js'))
    ).toEqual({
      apiOrigin: 'http://localhost:8081',
      headlineSelector: '[data-rainmaker-headline]',
      qualifiedReadingSeconds: 60,
    });
  });

  it('lets the publisher change the qualified reading threshold', () => {
    const element = script('http://localhost:8081/widget/rainmaker.js');
    element.dataset.qualifiedReadingSeconds = '90';

    expect(readSettings(element)?.qualifiedReadingSeconds).toBe(90);
  });

  it.each(['', 'abc', '0', '-5', '1.5', '60s', '3601'])(
    'falls back to 60 s for an invalid threshold: "%s"',
    value => {
      const element = script('http://localhost:8081/widget/rainmaker.js');
      element.dataset.qualifiedReadingSeconds = value;

      expect(readSettings(element)?.qualifiedReadingSeconds).toBe(60);
    }
  );

  it('lets the publisher choose the headline selector', () => {
    expect(
      readSettings(
        script('http://localhost:8081/widget/rainmaker.js', 'h1.title')
      )?.headlineSelector
    ).toBe('h1.title');
  });

  it('does not start without a script element or an http(s) address', () => {
    expect(readSettings(null)).toBeNull();
    expect(readSettings(document.createElement('script'))).toBeNull();
    expect(readSettings(script('data:text/javascript,0'))).toBeNull();
  });
});
