import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {TrashIcon} from '../../components/icons';
import {STORY_STATUS_TONE} from '../../data/statusTone';
import type {Snapshot, Story, StoryStatus} from '../../data/types';
import {StoryForm} from './StoryForm';
import {
  STORY_STATUSES,
  addStory,
  removeStory,
  setStoryStatus,
  storyProgress,
  type StoryDraft,
} from './stories';

/** Sprint stories. Edits live in component state only; nothing is saved in V1. */
export function StoryBoard({
  sprint,
  initialStories,
}: {
  sprint: Snapshot['development']['sprint'];
  initialStories: Story[];
}) {
  const {t} = useTranslation();
  const [stories, setStories] = useState(initialStories);
  const [showForm, setShowForm] = useState(false);
  const progress = storyProgress(stories);

  function add(draft: StoryDraft) {
    setStories(current => addStory(current, draft));
    setShowForm(false);
  }

  return (
    <section className="card">
      <div className="card__header story-board__header">
        <div className="item">
          <h2 className="card__title">
            {sprint
              ? t('development.sprintHeading', {
                  name: sprint.name,
                  dates: sprint.dates,
                })
              : t('development.noSprint')}
          </h2>
          <span className="kpi__note">
            {t('development.storySummary', {...progress})}
          </span>
        </div>
        <div className="story-board__controls">
          <div className="story-board__progress">
            <div className="progress progress--thick">
              <div
                className="progress__bar"
                style={{width: `${progress.percentDone}%`}}
              />
            </div>
            <span className="mono">
              {t('development.progress', {percent: progress.percentDone})}
            </span>
          </div>
          <button
            type="button"
            className="button button--primary"
            aria-expanded={showForm}
            onClick={() => setShowForm(!showForm)}
          >
            {t('development.addStory')}
          </button>
        </div>
      </div>

      {showForm && (
        <StoryForm onAdd={add} onCancel={() => setShowForm(false)} />
      )}

      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th>{t('development.columnId')}</th>
              <th>{t('development.columnStory')}</th>
              <th>{t('development.columnOwner')}</th>
              <th>{t('development.columnStatus')}</th>
              <th>
                <span className="sr-only">
                  {t('development.columnActions')}
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            {stories.map(story => (
              <tr key={story.id}>
                <td className="nowrap mono">{story.id}</td>
                <td>{story.title}</td>
                <td className="nowrap">
                  {story.owner || t('development.unassigned')}
                </td>
                <td className="table__compact">
                  <select
                    className={`pill pill--select pill--${STORY_STATUS_TONE[story.status]}`}
                    aria-label={t('development.statusOf', {id: story.id})}
                    value={story.status}
                    onChange={event =>
                      setStories(current =>
                        setStoryStatus(
                          current,
                          story.id,
                          event.target.value as StoryStatus
                        )
                      )
                    }
                  >
                    {STORY_STATUSES.map(status => (
                      <option key={status} value={status}>
                        {t(`storyStatus.${status}`)}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="table__compact align-right">
                  <button
                    type="button"
                    className="icon-button"
                    aria-label={t('development.remove', {id: story.id})}
                    onClick={() =>
                      setStories(current => removeStory(current, story.id))
                    }
                  >
                    <TrashIcon />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
