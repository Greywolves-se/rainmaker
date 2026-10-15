import {useTranslation} from 'react-i18next';
import {SearchIcon} from '../components/icons';
import {formatTimestamp} from '../i18n/format';
import {LanguageSwitch} from './LanguageSwitch';

export function TopBar({generatedAt}: {generatedAt: string}) {
  const {t, i18n} = useTranslation();
  return (
    <header className="top-bar">
      <div className="search">
        <SearchIcon />
        <label htmlFor="mc-search" className="sr-only">
          {t('topBar.searchLabel')}
        </label>
        {/* Search has no backend in V1, so the field is shown but disabled. */}
        <input
          id="mc-search"
          type="search"
          className="search__input"
          placeholder={t('topBar.searchPlaceholder')}
          title={t('topBar.notAvailable')}
          disabled
        />
      </div>
      <time className="clock" dateTime={generatedAt}>
        {formatTimestamp(generatedAt, i18n.language)}
      </time>
      <LanguageSwitch />
    </header>
  );
}
