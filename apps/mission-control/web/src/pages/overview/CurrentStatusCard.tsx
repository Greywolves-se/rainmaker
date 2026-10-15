import type {ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {SourceLink} from '../../components/SourceLink';
import {BlockedIcon, CheckIcon, ClockIcon} from '../../components/icons';
import type {LinkedItem, Snapshot} from '../../data/types';

type Overview = Snapshot['overview'];

export function CurrentStatusCard({overview}: {overview: Overview}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <div className="card__header">
        <h2 className="card__title">{t('overview.currentStatus')}</h2>
        <span className="small muted">
          {t('overview.currentStatusSources')}
        </span>
      </div>
      <div className="grid grid--status">
        <StatusColumn
          variant="done"
          icon={<CheckIcon />}
          heading={t('overview.doneCount', {count: overview.done.length})}
          items={overview.done}
          empty={t('overview.nothingDone')}
        />
        <StatusColumn
          variant="progress"
          icon={<ClockIcon />}
          heading={t('overview.inProgressCount', {
            count: overview.inProgress.length,
          })}
          items={overview.inProgress}
          empty={t('overview.nothingInProgress')}
        />
        <StatusColumn
          variant="blocked"
          icon={<BlockedIcon />}
          heading={t('overview.blockedCount', {count: overview.blocked.length})}
          items={overview.blocked}
          empty={t('overview.noBlockers')}
        />
      </div>
    </section>
  );
}

function StatusColumn({
  variant,
  icon,
  heading,
  items,
  empty,
}: {
  variant: 'done' | 'progress' | 'blocked';
  icon: ReactNode;
  heading: string;
  items: LinkedItem[];
  empty: string;
}) {
  return (
    <div className={`status-column status-column--${variant}`}>
      <h3>
        {icon}
        {heading}
      </h3>
      {items.length === 0 && <p className="item__meta">{empty}</p>}
      {items.map(item => (
        <ItemText
          key={item.id}
          title={<SourceLink url={item.url}>{item.title}</SourceLink>}
          meta={item.meta}
        />
      ))}
    </div>
  );
}
