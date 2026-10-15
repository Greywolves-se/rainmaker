import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {StatusPill} from '../../components/StatusPill';
import type {Snapshot} from '../../data/types';
import {formatMonth, formatSek} from '../../i18n/format';

type Overview = Snapshot['overview'];

/** Pilots and AWS cost; the risk callout itself is filled in #23. */
export function RisksPilotsCostCard({overview}: {overview: Overview}) {
  const {t, i18n} = useTranslation();
  const {awsCost, risk, pilotList} = overview;
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
      {risk && (
        <div className="callout">
          <span className="callout__title">
            {t('overview.risk', {title: risk.title})}
          </span>
          <span className="callout__text">{risk.text}</span>
        </div>
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
