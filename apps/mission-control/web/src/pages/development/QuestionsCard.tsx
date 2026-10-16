import {useTranslation} from 'react-i18next';
import {ItemText} from '../../components/ItemText';
import {NoData} from '../../components/NoData';
import {StatusPill} from '../../components/StatusPill';
import {isOverdue} from '../../data/risksAndQuestions';
import type {Snapshot} from '../../data/types';
import {formatDay} from '../../i18n/format';
import {useNow} from '../../layout/useNow';

type Development = Snapshot['development'];

/**
 * "Blockers and questions": open questions, nearest needed-by date first (#23). A question whose
 * needed-by day has passed gets an "Overdue" pill, so the state is shown as text, not only colour.
 */
export function QuestionsCard({
  audience,
  questions,
}: {
  audience: string | null;
  questions: Development['questions'];
}) {
  const {t, i18n} = useTranslation();
  const now = useNow();
  return (
    <section className="card">
      <h2 className="card__title">
        {audience
          ? t('development.questionsFor', {audience})
          : t('development.questions')}
      </h2>
      {questions.length === 0 && <NoData />}
      {questions.map(question => (
        <div key={question.id} className="row row--top question">
          <div className="question__tags">
            <StatusPill status={question.status} />
            {isOverdue(question.neededBy, now) && (
              <StatusPill status="overdue" />
            )}
            <span className="item__meta">{question.audience}</span>
          </div>
          <ItemText
            title={question.title}
            meta={
              question.neededBy
                ? t('development.neededBy', {
                    date: formatDay(question.neededBy, i18n.language),
                  })
                : question.meta
            }
          />
        </div>
      ))}
    </section>
  );
}
