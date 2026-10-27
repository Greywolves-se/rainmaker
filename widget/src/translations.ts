import english from './translations/en.json';
import swedish from './translations/sv.json';

/** The panel's texts. Every language file has the same keys as the Swedish one. */
export type Texts = typeof swedish;

// Typed as Texts so the build fails if en.json misses a key that sv.json has.
const englishTexts: Texts = english;

/** The texts for the page's language (`<html lang>`): Swedish for `sv`, otherwise English. */
export function textsFor(language: string): Texts {
  return language.toLowerCase().startsWith('sv') ? swedish : englishTexts;
}
