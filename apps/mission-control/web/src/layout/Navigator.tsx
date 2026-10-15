import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {NoData} from '../components/NoData';
import {SparkleIcon} from '../components/icons';
import type {Snapshot} from '../data/types';

export function Navigator({navigator}: {navigator: Snapshot['navigator']}) {
  const {t} = useTranslation();
  const [selectedQuestion, setSelectedQuestion] = useState(0);
  const answer = navigator.questions[selectedQuestion]?.answer ?? '';

  return (
    <aside className="navigator" aria-label={t('navigator.label')}>
      <div className="navigator__header">
        <span className="navigator__icon">
          <SparkleIcon />
        </span>
        <div className="item">
          <h2 className="navigator__title">{t('navigator.title')}</h2>
          {navigator.summaryTime && (
            <span className="item__meta">
              {t('navigator.summaryAt', {time: navigator.summaryTime})}
            </span>
          )}
        </div>
      </div>

      <div className="navigator__summary">
        <span className="navigator__summary-label">
          {t('navigator.managementSummary')}
        </span>
        {navigator.summary ? <p>{navigator.summary}</p> : <NoData />}
      </div>

      <div className="navigator__projects">
        {navigator.projects.map(project => (
          <div key={project.id} className="navigator__project">
            <div className="navigator__project-head">
              <span
                className={`dot dot--${project.health}`}
                aria-hidden="true"
              />
              <span className="strong">{project.title}</span>
              <span className="item__meta">
                {t(`health.${project.health}`)}
              </span>
            </div>
            <span className="navigator__project-text">{project.text}</span>
          </div>
        ))}
      </div>

      <div className="navigator__section">
        <h3>{t('navigator.nextSteps')}</h3>
        {navigator.nextSteps.length === 0 && <NoData />}
        <ol className="navigator__steps">
          {navigator.nextSteps.map((step, index) => (
            <li key={index}>{step}</li>
          ))}
        </ol>
      </div>

      <div className="navigator__section">
        <h3>{t('navigator.ask')}</h3>
        {navigator.questions.length === 0 && <NoData />}
        <div className="chips">
          {navigator.questions.map((entry, index) => (
            <button
              key={entry.id}
              type="button"
              className="chip"
              aria-pressed={index === selectedQuestion}
              onClick={() => setSelectedQuestion(index)}
            >
              {entry.question}
            </button>
          ))}
        </div>
        {answer && (
          <div className="answer" aria-live="polite">
            {answer}
          </div>
        )}
        {/* Free-text questions need an AI backend, which V1 does not have. */}
        <div className="ask-row">
          <label htmlFor="mc-ask" className="sr-only">
            {t('navigator.askOwnLabel')}
          </label>
          <input
            id="mc-ask"
            type="text"
            className="ask-row__input"
            placeholder={t('navigator.askOwnPlaceholder')}
            title={t('topBar.notAvailable')}
            disabled
          />
          <button type="button" className="button button--primary" disabled>
            {t('navigator.askButton')}
          </button>
        </div>
      </div>
    </aside>
  );
}
