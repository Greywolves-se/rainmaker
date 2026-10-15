import type {ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {BlockedIcon, CheckIcon, ClockIcon} from '../../components/icons';
import type {Snapshot, TitledItem} from '../../data/types';

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
        />
        <StatusColumn
          variant="progress"
          icon={<ClockIcon />}
          heading={t('overview.inProgressCount', {
            count: overview.inProgress.length,
          })}
          items={overview.inProgress}
        />
        <StatusColumn
          variant="blocked"
          icon={<BlockedIcon />}
          heading={t('overview.blockedCount', {count: overview.blocked.length})}
          items={overview.blocked}
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
}: {
  variant: 'done' | 'progress' | 'blocked';
  icon: ReactNode;
  heading: string;
  items: TitledItem[];
}) {
  return (
    <div className={`status-column status-column--${variant}`}>
      <h3>
        {icon}
        {heading}
      </h3>
      {items.map(item => (
        <ItemText key={item.id} title={item.title} meta={item.meta} />
      ))}
    </div>
  );
}
