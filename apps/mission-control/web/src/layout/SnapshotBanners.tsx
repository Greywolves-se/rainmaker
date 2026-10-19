import {useTranslation} from 'react-i18next';
import type {LoadedSnapshot} from '../data/snapshotClient';

/** Older snapshots get a warning: the collector should run at least once a day. */
export const STALE_AFTER_MS = 24 * 60 * 60 * 1000;

/** Tells the reader when the data cannot be trusted: demo data, old data or failed sources. */
export function SnapshotBanners({
  loaded,
  now,
}: {
  loaded: LoadedSnapshot;
  now: number;
}) {
  const {t} = useTranslation();
  if (loaded.demo) {
    return (
      <p className="sample-banner" role="note">
        {t('sampleData.banner')}
      </p>
    );
  }
  const stale = now - Date.parse(loaded.generatedAt) > STALE_AFTER_MS;
  return (
    <>
      {stale && (
        <p className="warning-banner" role="alert">
          {t('snapshot.stale')}
        </p>
      )}
      {loaded.failedSources.map(source => (
        <p key={source.id} className="warning-banner" role="alert">
          {source.message
            ? t('snapshot.sourceFailed', {
                name: source.name,
                message: source.message,
              })
            : t('snapshot.sourceFailedNoMessage', {name: source.name})}
        </p>
      ))}
    </>
  );
}
