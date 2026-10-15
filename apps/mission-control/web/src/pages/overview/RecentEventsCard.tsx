import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {NoData} from '../../components/NoData';
import {SourceTile} from '../../components/SourceTile';
import {UnavailableAction} from '../../components/UnavailableAction';
import type {Snapshot} from '../../data/types';

type Events = Snapshot['overview']['events'];

export function RecentEventsCard({events}: {events: Events}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <div className="card__header">
        <h2 className="card__title">{t('overview.recentEvents')}</h2>
        <UnavailableAction>{t('overview.viewAll')}</UnavailableAction>
      </div>
      {events.length === 0 && <NoData />}
      {events.map(event => (
        <div key={event.id} className="row row--plain">
          <SourceTile code={event.source} size="medium" />
          <div className="row__grow">
            <ItemText title={event.title} meta={event.meta} />
          </div>
        </div>
      ))}
    </section>
  );
}
