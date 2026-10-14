const LOCALE = 'en-GB';

/** The team works in Stockholm; show times there regardless of the viewer's machine. */
const TIME_ZONE = 'Europe/Stockholm';

/** "Wed 14 Oct 2026 · 10:24" style stamp for the top bar. */
export function formatTimestamp(isoTimestamp: string): string {
  const date = new Date(isoTimestamp);
  const day = new Intl.DateTimeFormat(LOCALE, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: TIME_ZONE,
  }).format(date);
  const time = new Intl.DateTimeFormat(LOCALE, {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: TIME_ZONE,
  }).format(date);
  return `${day} · ${time}`;
}

/** "2026-10" → "October". */
export function formatMonth(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number);
  if (!year || !month) {
    return yearMonth;
  }
  return new Intl.DateTimeFormat(LOCALE, {
    month: 'long',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, month - 1, 1)));
}

export function formatSek(amount: number): string {
  return new Intl.NumberFormat(LOCALE, {
    style: 'currency',
    currency: 'SEK',
    maximumFractionDigits: 0,
  }).format(amount);
}

/** ["Peter", "Staffan"] → "Peter or Staffan". */
export function formatAlternatives(names: readonly string[]): string {
  return new Intl.ListFormat(LOCALE, {type: 'disjunction'}).format(names);
}
