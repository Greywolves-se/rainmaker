import {describe, expect, it} from 'vitest';
import en from './en.json';
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

describe('translation file', () => {
  it('has no empty or non-string texts', () => {
    const invalid = leafValues(en)
      .filter(([, text]) => typeof text !== 'string' || text.trim() === '')
      .map(([path]) => path);

    expect(invalid).toEqual([]);
  });
});

describe('format', () => {
  it('shows the snapshot time in Stockholm time', () => {
    expect(formatTimestamp('2026-10-14T08:24:00Z')).toMatch(
      /14 Oct 2026 · 10:24$/
    );
  });

  it('names the month', () => {
    expect(formatMonth('2026-10')).toBe('October');
    expect(formatMonth('not-a-month')).toBe('not-a-month');
  });

  it('joins alternatives with or', () => {
    expect(formatAlternatives(['Peter', 'Staffan'])).toBe('Peter or Staffan');
  });

  it('formats SEK amounts without decimals', () => {
    expect(formatSek(1234)).toMatch(/SEK\s?1,234$/);
  });
});
