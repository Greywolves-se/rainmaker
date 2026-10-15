import {afterEach, describe, expect, it} from 'vitest';
import en from './en.json';
import sv from './sv.json';
import {LANGUAGE_STORAGE_KEY, createI18n, saveLanguage} from './i18n';
import {
  formatAlternatives,
  formatMonth,
  formatSek,
  formatTimestamp,
} from './format';

function leafValues(value: unknown, path = ''): [string, unknown][] {
  if (typeof value !== 'object' || value === null) {
    return [[path, value]];
  }
  return Object.entries(value).flatMap(([key, child]) =>
    leafValues(child, path ? `${path}.${key}` : key)
  );
}

function keyPaths(translations: unknown): string[] {
  return leafValues(translations)
    .map(([path]) => path)
    .sort();
}

describe('translation file', () => {
  it('has no empty or non-string texts', () => {
    const invalid = leafValues(en)
      .filter(([, text]) => typeof text !== 'string' || text.trim() === '')
      .map(([path]) => path);

    expect(invalid).toEqual([]);
  });

  it('has the same keys in Swedish and English', () => {
    expect(keyPaths(sv)).toEqual(keyPaths(en));
  });
});

describe('format', () => {
  it('shows the snapshot time in Stockholm time', () => {
    const timestamp = '2026-10-14T08:24:00Z';
    expect(formatTimestamp(timestamp, 'en')).toEqual({
      day: 'Wed, 14 Oct 2026',
      time: '10:24',
    });
    expect(formatTimestamp(timestamp, 'sv')).toEqual({
      day: 'ons 14 okt. 2026',
      time: '10:24',
    });
  });

  it('names the month', () => {
    expect(formatMonth('2026-10', 'en')).toBe('October');
    expect(formatMonth('2026-10', 'sv')).toBe('oktober');
    expect(formatMonth('not-a-month', 'sv')).toBe('not-a-month');
  });

  it('joins alternatives with or', () => {
    const names = ['Peter', 'Staffan'];
    expect(formatAlternatives(names, 'en')).toBe('Peter or Staffan');
    expect(formatAlternatives(names, 'sv')).toBe('Peter eller Staffan');
  });

  it('formats SEK amounts without decimals', () => {
    expect(formatSek(1234, 'en')).toMatch(/SEK\s?1,234$/);
    expect(formatSek(1234, 'sv')).toMatch(/^1\s234\s?kr$/);
  });
});

describe('language', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('is Swedish by default', () => {
    expect(createI18n().language).toBe('sv');
  });

  it('uses the language saved in the browser', () => {
    saveLanguage('en');
    expect(createI18n().language).toBe('en');
  });

  it('ignores an unknown saved language', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'de');
    expect(createI18n().language).toBe('sv');
  });
});
