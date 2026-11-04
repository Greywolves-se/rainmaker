import {describe, expect, it} from 'vitest';
import contractExample from '../../docs/rainmaker/contract/7.4-widget-links.json';
import {parseLinks} from './links-api';

const API_ORIGIN = 'http://localhost:8081';

function link(
  overrides: Record<string, unknown> = {}
): Record<string, unknown> {
  return {
    offerId: 'offer-1',
    advertiser: 'Vintra Kakel & Bad',
    title: 'Gratis hembesök',
    text: 'Fast pris inom en vecka.',
    clickUrl: 'http://localhost:8081/c/first',
    ...overrides,
  };
}

describe('parseLinks', () => {
  it('reads the links of a valid answer', () => {
    expect(parseLinks({links: [link()]}, API_ORIGIN)).toEqual([
      {
        advertiser: 'Vintra Kakel & Bad',
        title: 'Gratis hembesök',
        text: 'Fast pris inom en vecka.',
        clickUrl: 'http://localhost:8081/c/first',
      },
    ]);
  });

  it("reads G1's answer exactly as contract 7.4 shows it", () => {
    // The same file G1's tests check its answer against, so the two sides can't drift (#151).
    expect(parseLinks(contractExample, API_ORIGIN)).toEqual([
      {
        advertiser: 'Vintra Kakel & Bad',
        title: 'Gratis hembesök för badrumsoffert',
        text: 'Vi mäter upp och ger fast pris inom en vecka.',
        clickUrl: 'http://localhost:8081/c/eyJjIjoiMDE5MmYzZTQifQ.c2lnbmF0dXJl',
      },
    ]);
  });

  it.each([null, 'text', [], {}, {links: 'none'}])(
    'returns no links for an answer that is not the agreed format (%j)',
    body => {
      expect(parseLinks(body, API_ORIGIN)).toEqual([]);
    }
  );

  it('skips a link with a missing or non-text field', () => {
    const links = [
      link({title: undefined}),
      link({text: 42}),
      link({clickUrl: 'http://localhost:8081/c/ok'}),
    ];

    expect(
      parseLinks({links}, API_ORIGIN).map(parsed => parsed.clickUrl)
    ).toEqual(['http://localhost:8081/c/ok']);
  });

  it.each([
    'https://advertiser.example/offer',
    'javascript:alert(1)',
    'not a url',
    '/c/relative',
  ])(
    'skips a link whose click link is not on the widget API (%s)',
    clickUrl => {
      expect(parseLinks({links: [link({clickUrl})]}, API_ORIGIN)).toEqual([]);
    }
  );

  it('keeps at most three links, in the order given', () => {
    const links = [1, 2, 3, 4].map(number =>
      link({clickUrl: `http://localhost:8081/c/${number}`})
    );

    expect(
      parseLinks({links}, API_ORIGIN).map(parsed => parsed.clickUrl)
    ).toEqual([
      'http://localhost:8081/c/1',
      'http://localhost:8081/c/2',
      'http://localhost:8081/c/3',
    ]);
  });
});
