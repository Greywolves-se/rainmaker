import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {SourceLink} from '../../components/SourceLink';
import {SourceTile} from '../../components/SourceTile';
import type {Snapshot} from '../../data/types';
import {formatAge} from '../../i18n/format';
import {useNow} from '../../layout/useNow';

type Events = Snapshot['overview']['events'];

/** The events feed, newest first; each event links to its source. */
export function RecentEventsCard({events}: {events: Events}) {
  const {t, i18n} = useTranslation();
  const now = useNow();
  return (
    <section className="card">
      <h2 className="card__title">{t('overview.recentEvents')}</h2>
      {events.length === 0 && (
        <p className="item__meta">{t('overview.noEvents')}</p>
      )}
      {events.map(event => (
        <div key={event.id} className="row row--plain">
          <SourceTile code={event.source} size="medium" />
          <div className="row__grow">
            <ItemText
              title={<SourceLink url={event.url}>{event.title}</SourceLink>}
              meta={t('overview.eventMeta', {
                source: event.sourceName,
                ago: formatAge(event.occurredAt, now, i18n.language),
              })}
            />
          </div>
        </div>
      ))}
    </section>
  );
}
