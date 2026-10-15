import type {ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {NoData} from '../../components/NoData';
import {TrendIcon} from '../../components/icons';
import {STATUS_TONE} from '../../data/statusTone';
import type {Snapshot, StatusCode} from '../../data/types';

type Overview = Snapshot['overview'];

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
        {project ? (
          <>
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
          </>
        ) : (
          <NoData />
        )}
      </section>
      <section className="card card--kpi">
        <h2 className="kpi__label">{t('overview.operations')}</h2>
        {operations ? (
          <>
            <KpiStatus status={operations.status} />
            <span className="kpi__note">{operations.note}</span>
          </>
        ) : (
          <NoData />
        )}
      </section>
      <section className="card card--kpi">
        <h2 className="kpi__label">{t('overview.businessStatus')}</h2>
        {business ? (
          <>
            <KpiStatus status={business.status} icon={<TrendIcon />} />
            <span className="kpi__note">{business.note}</span>
          </>
        ) : (
          <NoData />
        )}
      </section>
      <section className="card card--kpi">
        <h2 className="kpi__label">{t('overview.activePilots')}</h2>
        {pilots ? (
          <>
            <div className="kpi__value">{pilots.active}</div>
            <span className="kpi__note">{pilots.note}</span>
          </>
        ) : (
          <NoData />
        )}
      </section>
    </div>
  );
}
