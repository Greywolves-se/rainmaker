import i18next, {type i18n} from 'i18next';
import {initReactI18next} from 'react-i18next';
import en from './en.json';

/**
 * English only for now. All UI text still goes through translation keys, so adding
 * Swedish later is a new `sv.json` plus a language switch.
 */
export function createI18n(): i18n {
  const instance = i18next.createInstance();
  void instance.use(initReactI18next).init({
    resources: {en: {translation: en}},
    lng: 'en',
    fallbackLng: 'en',
    interpolation: {escapeValue: false},
    initImmediate: false,
  });
  return instance;
}
