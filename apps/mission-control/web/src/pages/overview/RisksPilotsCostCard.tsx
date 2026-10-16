import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {StatusPill} from '../../components/StatusPill';
import {RISK_STATUS} from '../../data/risksAndQuestions';
import {STATUS_TONE} from '../../data/statusTone';
import type {Snapshot} from '../../data/types';
import {formatMonth, formatSek} from '../../i18n/format';

type Overview = Snapshot['overview'];

/** The card shows the most serious risks; a line says how many more there are. */
const MAX_RISKS = 3;

/** Pilots, the highest risks (red first, with owner and mitigation) and AWS cost (#23). */
export function RisksPilotsCostCard({overview}: {overview: Overview}) {
  const {t, i18n} = useTranslation();
  const {awsCost, risks, pilotList} = overview;
  return (
    <section className="card">
      <h2 className="card__title">{t('overview.risksPilotsCost')}</h2>
      {pilotList.length === 0 && (
        <p className="item__meta">{t('overview.noPilots')}</p>
      )}
      {pilotList.map(pilot => (
        <div key={pilot.id} className="row row--spread">
          <ItemText title={pilot.title} meta={pilot.meta} />
          <StatusPill status={pilot.status} />
        </div>
      ))}
      {risks.length === 0 && (
        <p className="item__meta">{t('overview.noRisks')}</p>
      )}
      {risks.slice(0, MAX_RISKS).map(risk => (
        <div
          key={risk.id}
          className={`callout callout--${STATUS_TONE[RISK_STATUS[risk.level]]}`}
        >
          <div className="row row--spread">
            <span className="callout__title">
              {t('overview.risk', {title: risk.title})}
            </span>
            <StatusPill status={RISK_STATUS[risk.level]} />
          </div>
          <span className="callout__text">{risk.mitigation}</span>
          <span className="item__meta">
            {t('overview.riskOwner', {owner: risk.owner})}
          </span>
        </div>
      ))}
      {risks.length > MAX_RISKS && (
        <p className="item__meta">
          {t('overview.moreRisks', {count: risks.length - MAX_RISKS})}
        </p>
      )}
      <div className="row row--spread">
        <span className="muted">
          {awsCost
            ? t('overview.awsCost', {
                month: formatMonth(awsCost.month, i18n.language),
              })
            : t('overview.awsCostLabel')}
        </span>
        <span className="mono">
          {!awsCost
            ? t('overview.notConnected')
            : awsCost.amountSek === null
              ? t('overview.amountUnknown')
              : formatSek(awsCost.amountSek, i18n.language)}
        </span>
      </div>
    </section>
  );
}
