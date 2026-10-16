import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {SourceLink} from '../../components/SourceLink';
import {decisionMeta} from '../../components/decisionMeta';
import type {Snapshot} from '../../data/types';
import {formatAlternatives} from '../../i18n/format';

type Overview = Snapshot['overview'];

/** Decisions waiting for Peter or Staffan, oldest first; each links to its line on GitHub. */
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
      {overview.decisions.length === 0 && (
        <p className="item__meta">{t('overview.noDecisionsWaiting')}</p>
      )}
      {overview.decisions.map(decision => (
        <div key={decision.id} className="row row--top">
          <span className="avatar" aria-hidden="true">
            {decision.owner.charAt(0)}
          </span>
          <div className="row__grow">
            <ItemText
              title={
                <SourceLink url={decision.url}>{decision.title}</SourceLink>
              }
              meta={decisionMeta(decision, t, i18n.language)}
            />
          </div>
        </div>
      ))}
    </section>
  );
}
