import type {Language} from './i18n';

/** Intl locale per app language. en-GB gives "14 Oct 2026" instead of the US "Oct 14". */
const LOCALES: Record<Language, string> = {sv: 'sv-SE', en: 'en-GB'};

/** The team works in Stockholm; show times there regardless of the viewer's machine. */
const TIME_ZONE = 'Europe/Stockholm';

/** i18next gives the language as a plain string; unknown values fall back to Swedish. */
function localeFor(language: string): string {
  return LOCALES[language as Language] ?? LOCALES.sv;
}

export function formatTimestamp(
  isoTimestamp: string,
  language: string
): string {
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
  return `${day} · ${time}`;
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
