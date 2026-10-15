import i18next, {type i18n} from 'i18next';
import {initReactI18next} from 'react-i18next';
import en from './en.json';
import sv from './sv.json';

export const LANGUAGES = ['sv', 'en'] as const;
export type Language = (typeof LANGUAGES)[number];

/** Key in the browser's localStorage that remembers the chosen language. */
export const LANGUAGE_STORAGE_KEY = 'missionControl.language';

const DEFAULT_LANGUAGE: Language = 'sv';

function isLanguage(value: unknown): value is Language {
  return LANGUAGES.includes(value as Language);
}

/** The saved language, or Swedish when nothing valid is saved or storage is blocked. */
function readSavedLanguage(): Language {
  try {
    const saved = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    return isLanguage(saved) ? saved : DEFAULT_LANGUAGE;
  } catch {
    return DEFAULT_LANGUAGE;
  }
}

/** Remembers the choice for the next visit. */
export function saveLanguage(language: Language): void {
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch {
    // Private mode or blocked storage: the choice only lasts for this visit.
  }
}

export function createI18n(language: Language = readSavedLanguage()): i18n {
  const instance = i18next.createInstance();
  void instance.use(initReactI18next).init({
    resources: {sv: {translation: sv}, en: {translation: en}},
    lng: language,
    fallbackLng: DEFAULT_LANGUAGE,
    interpolation: {escapeValue: false},
    initImmediate: false,
  });
  return instance;
}
