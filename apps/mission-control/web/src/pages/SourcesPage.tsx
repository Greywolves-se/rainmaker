import {useTranslation} from 'react-i18next';
import {SourceTile} from '../components/SourceTile';
import {StatusPill} from '../components/StatusPill';
import {UnavailableAction} from '../components/UnavailableAction';
import type {Snapshot} from '../data/types';
import {useSnapshot} from '../data/useSnapshot';

type Sources = Snapshot['sources'];

/** Fixed explanation of how Mission Control is built; texts live under `sources.steps`. */
const PRINCIPLE_STEPS = [
  'fetch',
  'normalize',
  'show',
  'summarize',
  'goToSource',
] as const;

export function SourcesPage() {
  const {sources} = useSnapshot();
  return (
    <div className="stack">
      <SourceStats cards={sources.cards} />
      <BuildPrinciple />
      <div className="grid grid--sources">
        {sources.cards.map(card => (
          <SourceCard key={card.code} card={card} />
        ))}
      </div>
      <Mapping mapping={sources.mapping} />
      <SyncLog entries={sources.syncLog} />
    </div>
  );
}

function SourceStats({cards}: {cards: Sources['cards']}) {
  const {t} = useTranslation();
  const synced = cards.filter(card => card.status === 'synced').length;
  return (
    <section className="card source-stats">
      <div className="item">
        <span className="item__meta">{t('sources.connected')}</span>
        <span className="source-stats__number">{cards.length}</span>
      </div>
      <div className="item">
        <span className="item__meta">{t('sources.synced')}</span>
        <span className="source-stats__number source-stats__number--green">
          {synced}
        </span>
      </div>
      <div className="item">
        <span className="item__meta">{t('sources.needsAction')}</span>
        <span className="source-stats__number source-stats__number--amber">
          {cards.length - synced}
        </span>
      </div>
      <p className="source-stats__note">{t('sources.readOnlyNote')}</p>
      <UnavailableAction className="button button--primary">
        {t('sources.addSource')}
      </UnavailableAction>
    </section>
  );
}

function BuildPrinciple() {
  const {t} = useTranslation();
  return (
    <section className="card card--dark">
      <h2 className="card__title">{t('sources.buildPrinciple')}</h2>
      <ol className="principle-steps">
        {PRINCIPLE_STEPS.map((step, index) => (
          <li key={step} className="principle-step">
            <span className="principle-step__number">
              {String(index + 1).padStart(2, '0')}
            </span>
            <span className="principle-step__title">
              {t(`sources.steps.${step}.title`)}
            </span>
            <span className="principle-step__text">
              {t(`sources.steps.${step}.text`)}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}

function SourceCard({card}: {card: Sources['cards'][number]}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <div className="row row--plain">
        <SourceTile code={card.code} size="large" />
        <div className="row__grow item">
          <h3 className="source-card__title">{card.name}</h3>
          <span className="item__meta">{card.account}</span>
        </div>
        <StatusPill status={card.status} />
      </div>
      <p className="source-card__role">“{card.role}”</p>
      <ul className="source-card__tags">
        {card.tags.map((tag, index) => (
          <li key={index} className="tag">
            {tag}
          </li>
        ))}
      </ul>
      <dl className="source-card__facts">
        <div className="source-card__fact">
          <dt>{t('sources.scope')}</dt>
          <dd>{card.scope}</dd>
        </div>
        <div className="source-card__fact">
          <dt>{t('sources.mapsTo')}</dt>
          <dd>{card.mapsTo}</dd>
        </div>
        <div className="source-card__fact">
          <dt>{t('sources.updates')}</dt>
          <dd>{card.updates}</dd>
        </div>
      </dl>
      <div className="row row--spread source-card__footer">
        <span className="item__meta">{card.lastSync}</span>
        <UnavailableAction>{t('sources.openSource')}</UnavailableAction>
      </div>
    </section>
  );
}

function Mapping({mapping}: {mapping: Sources['mapping']}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <div className="item">
        <h2 className="card__title">{t('sources.howTheyConnect')}</h2>
        <span className="kpi__note">{t('sources.howTheyConnectSubtitle')}</span>
      </div>
      <div className="table-scroll">
        <table className="table table--wide">
          <thead>
            <tr>
              <th>{t('sources.columnConcept')}</th>
              <th>{t('sources.columnFrom')}</th>
              <th>{t('sources.columnExample')}</th>
            </tr>
          </thead>
          <tbody>
            {mapping.map(row => (
              <tr key={row.id}>
                <td className="mapping__concept">{row.concept}</td>
                <td>{row.from}</td>
                <td className="mapping__example">{row.example}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function SyncLog({entries}: {entries: Sources['syncLog']}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <h2 className="card__title">{t('sources.syncLog')}</h2>
      {entries.map(entry => (
        <div key={entry.id} className="row sync-log__row">
          <span className="sync-log__time">{entry.time}</span>
          <SourceTile code={entry.source} size="small" />
          <span className="sync-log__text">{entry.text}</span>
          <StatusPill status={entry.status} />
        </div>
      ))}
    </section>
  );
}
