import type {ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {ItemText} from '../components/ItemText';
import {SourceTile} from '../components/SourceTile';
import {StatusPill} from '../components/StatusPill';
import {UnavailableAction} from '../components/UnavailableAction';
import {
  BlockedIcon,
  CheckIcon,
  ClockIcon,
  TrendIcon,
} from '../components/icons';
import {STATUS_TONE} from '../data/statusTone';
import type {Snapshot, StatusCode, TitledItem} from '../data/types';
import {useSnapshot} from '../data/useSnapshot';
import {formatAlternatives, formatMonth, formatSek} from '../i18n/format';

type Overview = Snapshot['overview'];

export function OverviewPage() {
  const {overview} = useSnapshot();
  return (
    <div className="stack">
      <KpiCards overview={overview} />
      <CurrentStatus overview={overview} />
      <div className="grid">
        <Decisions overview={overview} />
        <RisksPilotsCost overview={overview} />
      </div>
      <div className="grid">
        <LatestRelease release={overview.release} />
        <RecentEvents events={overview.events} />
      </div>
    </div>
  );
}

/** Big status text in a KPI card. Colour follows STATUS_TONE; the icon defaults to a dot. */
function KpiStatus({status, icon}: {status: StatusCode; icon?: ReactNode}) {
  const {t} = useTranslation();
  const tone = STATUS_TONE[status];
  return (
    <div className={`kpi__value kpi__value--${tone}`}>
      {icon ?? (
        <span className={`dot dot--large dot--${tone}`} aria-hidden="true" />
      )}
      {t(`status.${status}`)}
    </div>
  );
}

export function KpiCards({overview}: {overview: Overview}) {
  const {t} = useTranslation();
  const {project, operations, business, pilots} = overview;
  return (
    <div className="grid grid--kpi">
      <section className="card card--kpi">
        <h2 className="kpi__label">{t('overview.projectStatus')}</h2>
        <KpiStatus status={project.status} />
        <span className="kpi__note">
          {t('overview.sprintProgress', {
            sprint: project.sprint,
            percent: project.percentDone,
          })}
        </span>
        <div className="progress">
          <div
            className="progress__bar"
            style={{width: `${project.percentDone}%`}}
          />
        </div>
      </section>
      <section className="card card--kpi">
        <h2 className="kpi__label">{t('overview.operations')}</h2>
        <KpiStatus status={operations.status} />
        <span className="kpi__note">{operations.note}</span>
      </section>
      <section className="card card--kpi">
        <h2 className="kpi__label">{t('overview.businessStatus')}</h2>
        <KpiStatus status={business.status} icon={<TrendIcon />} />
        <span className="kpi__note">{business.note}</span>
      </section>
      <section className="card card--kpi">
        <h2 className="kpi__label">{t('overview.activePilots')}</h2>
        <div className="kpi__value">{pilots.active}</div>
        <span className="kpi__note">{pilots.note}</span>
      </section>
    </div>
  );
}

function CurrentStatus({overview}: {overview: Overview}) {
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

function Decisions({overview}: {overview: Overview}) {
  const {t, i18n} = useTranslation();
  const names = formatAlternatives(overview.decisionMakers, i18n.language);
  return (
    <section className="card">
      <h2 className="card__title">{t('overview.decisionsFor', {names})}</h2>
      {overview.decisions.map(decision => (
        <div key={decision.id} className="row row--top">
          <span className="avatar" aria-hidden="true">
            {decision.initial}
          </span>
          <div className="row__grow">
            <ItemText title={decision.title} meta={decision.meta} />
          </div>
          <UnavailableAction className="button button--secondary button--small">
            {t('overview.open')}
          </UnavailableAction>
        </div>
      ))}
    </section>
  );
}

function RisksPilotsCost({overview}: {overview: Overview}) {
  const {t, i18n} = useTranslation();
  const {awsCost, risk} = overview;
  const amount =
    awsCost.amountSek === null
      ? t('overview.amountUnknown')
      : formatSek(awsCost.amountSek, i18n.language);
  return (
    <section className="card">
      <h2 className="card__title">{t('overview.risksPilotsCost')}</h2>
      {overview.pilotList.map(pilot => (
        <div key={pilot.id} className="row row--spread">
          <ItemText title={pilot.title} meta={pilot.meta} />
          <StatusPill status={pilot.status} />
        </div>
      ))}
      <div className="callout">
        <span className="callout__title">
          {t('overview.risk', {title: risk.title})}
        </span>
        <span className="callout__text">{risk.text}</span>
      </div>
      <div className="row row--spread">
        <span className="muted">
          {t('overview.awsCost', {
            month: formatMonth(awsCost.month, i18n.language),
          })}
        </span>
        <span className="mono">{amount}</span>
      </div>
    </section>
  );
}

function LatestRelease({release}: {release: Overview['release']}) {
  const {t} = useTranslation();
  return (
    <section className="card card--dark">
      <h2 className="card__title">{t('overview.latestVersion')}</h2>
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
    </section>
  );
}

function RecentEvents({events}: {events: Overview['events']}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <div className="card__header">
        <h2 className="card__title">{t('overview.recentEvents')}</h2>
        <UnavailableAction>{t('overview.viewAll')}</UnavailableAction>
      </div>
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
