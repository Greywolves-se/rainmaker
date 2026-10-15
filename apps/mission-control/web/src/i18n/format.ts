import type {Language} from './i18n';

/** Intl locale per app language. en-GB gives "14 Oct 2026" instead of the US "Oct 14". */
const LOCALES: Record<Language, string> = {sv: 'sv-SE', en: 'en-GB'};

/** The team works in Stockholm; show times there regardless of the viewer's machine. */
const TIME_ZONE = 'Europe/Stockholm';

/** i18next gives the language as a plain string; unknown values fall back to Swedish. */
function localeFor(language: string): string {
  return LOCALES[language as Language] ?? LOCALES.sv;
}

/** Day and time for the top bar; the translation file joins them ("{{day}} · {{time}}"). */
export function formatTimestamp(
  isoTimestamp: string,
  language: string
): {day: string; time: string} {
  const locale = localeFor(language);
  const date = new Date(isoTimestamp);
  const day = new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: TIME_ZONE,
  }).format(date);
  const time = new Intl.DateTimeFormat(locale, {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: TIME_ZONE,
  }).format(date);
  return {day, time};
}

/** "5 minutes ago" / "för 5 minuter sedan"; hours from 1 hour, days from 2 days. */
export function formatAge(
  isoTimestamp: string,
  now: number,
  language: string
): string {
  const minutes = Math.max(
    0,
    Math.round((now - Date.parse(isoTimestamp)) / 60_000)
  );
  const format = new Intl.RelativeTimeFormat(localeFor(language), {
    numeric: 'auto',
  });
  if (minutes < 60) {
    return format.format(-minutes, 'minute');
  }
  const hours = Math.round(minutes / 60);
  if (hours < 48) {
    return format.format(-hours, 'hour');
  }
  return format.format(-Math.round(hours / 24), 'day');
}

/** Whole days from today in Stockholm to a date like "2026-12-18"; negative when passed. */
export function daysUntil(isoDate: string, now: number): number {
  // sv-SE writes dates as YYYY-MM-DD, so this is today's Stockholm date in the same form.
  const today = new Intl.DateTimeFormat('sv-SE', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
  return Math.round((Date.parse(isoDate) - Date.parse(today)) / 86_400_000);
}

/** "2026-10-26" → "26 Oct" / "26 okt.". */
export function formatDay(isoDate: string, language: string): string {
  return new Intl.DateTimeFormat(localeFor(language), {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(Date.parse(isoDate)));
}

/** "2026-10" → "October" / "oktober". (Example)*/
export function formatMonth(yearMonth: string, language: string): string {
  const [year, month] = yearMonth.split('-').map(Number);
  if (!year || !month) {
    return yearMonth;
  }
  return new Intl.DateTimeFormat(localeFor(language), {
    month: 'long',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, month - 1, 1)));
}

export function formatSek(amount: number, language: string): string {
  return new Intl.NumberFormat(localeFor(language), {
    style: 'currency',
    currency: 'SEK',
    maximumFractionDigits: 0,
  }).format(amount);
}

/** ["Peter", "Staffan"] → "Peter or Staffan" / "Peter eller Staffan". */
export function formatAlternatives(
  names: readonly string[],
  language: string
): string {
  return new Intl.ListFormat(localeFor(language), {type: 'disjunction'}).format(
    names
  );
}
