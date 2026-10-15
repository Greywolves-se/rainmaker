import {useTranslation} from 'react-i18next';
import {NoData} from '../../components/NoData';
import {UnavailableAction} from '../../components/UnavailableAction';
import type {Snapshot} from '../../data/types';

type Release = Snapshot['overview']['release'];

export function LatestReleaseCard({release}: {release: Release}) {
  const {t} = useTranslation();
  return (
    <section className="card card--dark">
      <h2 className="card__title">{t('overview.latestVersion')}</h2>
      {release ? <ReleaseDetails release={release} /> : <NoData />}
    </section>
  );
}

function ReleaseDetails({release}: {release: NonNullable<Release>}) {
  const {t} = useTranslation();
  return (
    <>
      <div className="release">
        <span className="release__version">{release.version}</span>
        <span className="release__badge">{release.badge}</span>
      </div>
      <p className="release__notes">{release.notes}</p>
      <div className="release__actions">
        <UnavailableAction className="button button--primary">
          {t('overview.openDemo')}
        </UnavailableAction>
        <UnavailableAction className="button button--ghost-dark">
          {t('overview.releaseNotes')}
        </UnavailableAction>
      </div>
    </>
  );
}
