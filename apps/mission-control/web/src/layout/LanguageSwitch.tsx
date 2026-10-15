import {useTranslation} from 'react-i18next';
import {LANGUAGES, saveLanguage, type Language} from '../i18n/i18n';

/** Swedish/English toggle. Each language is named in its own language. */
export function LanguageSwitch() {
  const {t, i18n} = useTranslation();

  function choose(language: Language) {
    void i18n.changeLanguage(language);
    saveLanguage(language);
  }

  return (
    <div className="chips" role="group" aria-label={t('language.label')}>
      {LANGUAGES.map(language => (
        <button
          key={language}
          type="button"
          className="chip"
          lang={language}
          aria-pressed={i18n.language === language}
          onClick={() => choose(language)}
        >
          {t(`language.${language}`)}
        </button>
      ))}
    </div>
  );
}
