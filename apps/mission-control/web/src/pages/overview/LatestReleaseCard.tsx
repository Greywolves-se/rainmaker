import {useTranslation} from 'react-i18next';
import {SourceLink} from '../../components/SourceLink';
import {UnavailableAction} from '../../components/UnavailableAction';
import type {Snapshot} from '../../data/types';
import {formatAge} from '../../i18n/format';
import {useNow} from '../../layout/useNow';

type Release = Snapshot['overview']['release'];

export function LatestReleaseCard({release}: {release: Release}) {
  const {t} = useTranslation();
  return (
    <section className="card card--dark">
      <h2 className="card__title">{t('overview.latestVersion')}</h2>
      {release ? (
        <ReleaseDetails release={release} />
      ) : (
        <p className="release__notes">{t('overview.noRelease')}</p>
      )}
    </section>
  );
}

function ReleaseDetails({release}: {release: NonNullable<Release>}) {
  const {t, i18n} = useTranslation();
  const now = useNow();
  return (
    <>
      <div className="release">
        <span className="release__version">{release.version}</span>
        <span className="release__badge">
          {t('overview.releaseMeta', {
            environment: t(`environment.${release.environment}`),
            ago: formatAge(release.publishedAt, now, i18n.language),
          })}
        </span>
      </div>
      <div className="release__actions">
        {/* No demo address in the snapshot yet; the button stays disabled until there is one. */}
        <UnavailableAction className="button button--primary">
          {t('overview.openDemo')}
        </UnavailableAction>
        <SourceLink url={release.url}>
          <span className="button button--ghost-dark">
            {t('overview.releaseNotes')}
          </span>
        </SourceLink>
      </div>
    </>
  );
}
