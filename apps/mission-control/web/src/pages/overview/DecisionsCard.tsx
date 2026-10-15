import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {NoData} from '../../components/NoData';
import {UnavailableAction} from '../../components/UnavailableAction';
import type {Snapshot} from '../../data/types';
import {formatAlternatives} from '../../i18n/format';

type Overview = Snapshot['overview'];

export function DecisionsCard({overview}: {overview: Overview}) {
  const {t, i18n} = useTranslation();
  const names = formatAlternatives(overview.decisionMakers, i18n.language);
  return (
    <section className="card">
      <h2 className="card__title">
        {names
          ? t('overview.decisionsFor', {names})
          : t('overview.decisionsPending')}
      </h2>
      {overview.decisions.length === 0 && <NoData />}
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
