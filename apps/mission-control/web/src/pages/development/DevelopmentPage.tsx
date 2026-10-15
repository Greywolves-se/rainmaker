import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {StatusPill} from '../../components/StatusPill';
import {CheckIcon} from '../../components/icons';
import type {Snapshot} from '../../data/types';
import {useSnapshot} from '../../data/useSnapshot';
import {StoryBoard} from './StoryBoard';

type Development = Snapshot['development'];

export function DevelopmentPage() {
  const {development} = useSnapshot();
  return (
    <div className="stack">
      <StoryBoard
        sprint={development.sprint}
        initialStories={development.stories}
      />
      <div className="grid">
        <PullRequests pullRequests={development.pullRequests} />
        <LatestBuild build={development.build} />
      </div>
      <div className="grid">
        <TechDecisions decisions={development.techDecisions} />
        <Questions
          audience={development.questionAudience}
          questions={development.questions}
        />
      </div>
      <Environments links={development.links} />
    </div>
  );
}

function PullRequests({
  pullRequests,
}: {
  pullRequests: Development['pullRequests'];
}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <h2 className="card__title">{t('development.pullRequests')}</h2>
      {pullRequests.map(pullRequest => (
        <div key={pullRequest.number} className="row row--spread">
          <ItemText
            title={
              <>
                <span className="mono muted">{pullRequest.number}</span>{' '}
                {pullRequest.title}
              </>
            }
            meta={pullRequest.meta}
          />
          <StatusPill status={pullRequest.status} />
        </div>
      ))}
    </section>
  );
}

function LatestBuild({build}: {build: Development['build']}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <h2 className="card__title">{t('development.latestBuild')}</h2>
      <div className="row row--plain">
        <span className="build__badge">
          <CheckIcon size={20} strokeWidth={2.6} />
        </span>
        <div className="item">
          <span className="strong">
            {t('development.buildPassed', {
              branch: build.branch,
              number: build.number,
            })}
          </span>
          <span className="item__meta">
            {t('development.buildMeta', {
              age: build.age,
              duration: build.duration,
            })}
          </span>
        </div>
      </div>
      <div className="build__facts">
        <BuildFact label={t('development.unitTests')} value={build.unitTests} />
        <BuildFact label={t('development.lint')} value={build.lint} />
        <BuildFact label={t('development.staging')} value={build.staging} />
      </div>
      <ul className="build__steps">
        {build.steps.map(step => (
          <li key={step}>{t('development.stepPassed', {step})}</li>
        ))}
      </ul>
    </section>
  );
}

function BuildFact({label, value}: {label: string; value: string}) {
  return (
    <div className="build__fact">
      <span className="item__meta">{label}</span>
      <span className="strong">{value}</span>
    </div>
  );
}

function TechDecisions({decisions}: {decisions: Development['techDecisions']}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <h2 className="card__title">{t('development.techDecisions')}</h2>
      {decisions.map(decision => (
        <div key={decision.title} className="row row--top row--spread">
          <ItemText title={decision.title} meta={decision.meta} />
          <StatusPill status={decision.status} />
        </div>
      ))}
    </section>
  );
}

function Questions({
  audience,
  questions,
}: {
  audience: string;
  questions: Development['questions'];
}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <h2 className="card__title">
        {t('development.questionsFor', {audience})}
      </h2>
      {questions.map(question => (
        <div key={question.title} className="row row--top question">
          <div className="question__tags">
            <StatusPill status={question.status} />
            <span className="item__meta">{question.audience}</span>
          </div>
          <ItemText title={question.title} meta={question.meta} />
        </div>
      ))}
    </section>
  );
}

function Environments({links}: {links: Development['links']}) {
  const {t} = useTranslation();
  return (
    <section className="card">
      <h2 className="card__title">{t('development.environments')}</h2>
      <div className="link-grid">
        {links.map(link => (
          <div key={link.title} className="link-tile">
            <span className="strong">{link.title}</span>
            <span className="item__meta">{link.meta}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
