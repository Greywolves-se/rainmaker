import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {NoData} from '../../components/NoData';
import {SourceLink} from '../../components/SourceLink';
import type {LatestDocuments} from '../../data/types';
import {formatAge} from '../../i18n/format';
import {useNow} from '../../layout/useNow';

/** The latest documents (#114), newest first; each opens in its source, e.g. Google Drive. */
export function LatestDocumentsCard({documents}: {documents: LatestDocuments}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <h2 className="card__title">{t('overview.latestDocuments')}</h2>
      {documents.status === 'unavailable' && <NoData />}
      {documents.status === 'off' && (
        <p className="item__meta">
          {t('overview.documentsOff', {sources: documents.sources.join(', ')})}
        </p>
      )}
      {documents.status === 'ok' && <DocumentList items={documents.items} />}
    </section>
  );
}

function DocumentList({
  items,
}: {
  items: Extract<LatestDocuments, {status: 'ok'}>['items'];
}) {
  const {t, i18n} = useTranslation();
  const now = useNow();
  if (items.length === 0) {
    return <p className="item__meta">{t('overview.noDocuments')}</p>;
  }
  return (
    <>
      {items.map(entry => {
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
    </>
  );
}
