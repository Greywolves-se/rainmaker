import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {NoData} from '../../components/NoData';
import {SourceLink} from '../../components/SourceLink';
import type {Snapshot} from '../../data/types';
import {formatAge} from '../../i18n/format';
import {useNow} from '../../layout/useNow';

type Documents = Snapshot['overview']['documents'];

/** The latest Google Drive documents (#114), newest first; each opens in Drive. */
export function LatestDocumentsCard({documents}: {documents: Documents}) {
  const {t, i18n} = useTranslation();
  const now = useNow();
  return (
    <section className="card">
      <h2 className="card__title">{t('overview.latestDocuments')}</h2>
      {documents === null && <NoData />}
      {documents?.length === 0 && (
        <p className="item__meta">{t('overview.noDocuments')}</p>
      )}
      {documents?.map(entry => {
        const values = {
          kind: t(`documentKind.${entry.kind}`),
          editor: entry.lastEditor,
          ago: formatAge(entry.modifiedAt, now, i18n.language),
        };
        return (
          <ItemText
            key={entry.id}
            title={<SourceLink url={entry.url}>{entry.title}</SourceLink>}
            meta={
              entry.lastEditor
                ? t('overview.documentMeta', values)
                : t('overview.documentMetaNoEditor', values)
            }
          />
        );
      })}
    </section>
  );
}
