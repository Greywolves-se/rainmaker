import type {ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {Link} from 'react-router';
import {SourceLink} from '../../components/SourceLink';
import type {Snapshot} from '../../data/types';
import {daysUntil} from '../../i18n/format';
import {useNow} from '../../layout/useNow';

type Overview = Snapshot['overview'];

/** Sprint progress, items blocked in this sprint, latest release and one countdown per key date. */
export function KpiCards({overview}: {overview: Overview}) {
  const {t} = useTranslation();
  const {sprint, release} = overview;
  return (
    <div className="grid grid--kpi">
      <KpiCard title={t('overview.sprintProgress')}>
        {sprint ? (
          <SourceLink url={sprint.url}>
            <span className="kpi__value">
              {t('overview.sprintDone', {
                done: sprint.done,
                total: sprint.total,
              })}
            </span>
          </SourceLink>
        ) : (
          <span className="kpi__note">{t('development.noSprint')}</span>
        )}
        {sprint && <span className="kpi__note">{sprint.name}</span>}
      </KpiCard>
      <KpiCard title={t('overview.blockedItems')}>
        <span className="kpi__value">{overview.blocked.length}</span>
        <Link className="kpi__note" to="/development">
          {t('overview.seeDevelopment')}
        </Link>
      </KpiCard>
      <KpiCard title={t('overview.latestRelease')}>
        {release ? (
          <SourceLink url={release.url}>
            <span className="kpi__value mono">{release.version}</span>
          </SourceLink>
        ) : (
          <span className="kpi__note">{t('overview.noRelease')}</span>
        )}
        {release && (
          <span className="kpi__note">
            {t(`environment.${release.environment}`)}
          </span>
        )}
      </KpiCard>
      {overview.milestones.map(milestone => (
        <Countdown key={milestone.id} {...milestone} />
      ))}
    </div>
  );
}

function KpiCard({title, children}: {title: string; children: ReactNode}) {
  return (
    <section className="card card--kpi">
      <h2 className="kpi__label">{title}</h2>
      {children}
    </section>
  );
}

/** Days left to a key date. Milestones have no source link in the contract (#10). */
function Countdown({title, date}: {title: string; date: string}) {
  const {t} = useTranslation();
  const days = daysUntil(date, useNow());
  return (
    <KpiCard title={title}>
      <span className="kpi__value">
        {days > 0
          ? t('overview.daysLeft', {count: days})
          : days === 0
            ? t('overview.milestoneToday')
            : t('overview.milestonePassed')}
      </span>
      <span className="kpi__note">{date}</span>
    </KpiCard>
  );
}
