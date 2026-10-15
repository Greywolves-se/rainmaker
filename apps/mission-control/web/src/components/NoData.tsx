import {useTranslation} from 'react-i18next';

/** Shown where the snapshot has no data for a section yet. */
export function NoData() {
  const {t} = useTranslation();
  return <p className="item__meta">{t('common.noData')}</p>;
}
