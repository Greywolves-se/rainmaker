import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {SourceLink} from '../../components/SourceLink';
import {STORY_STATUS_TONE} from '../../data/statusTone';
import type {Snapshot, Story} from '../../data/types';
import {formatAge, formatDay} from '../../i18n/format';
import {useNow} from '../../layout/useNow';
import {
  DEFAULT_FILTERS,
  effectiveFilters,
  filterChoices,
  filterStories,
  sprintProgress,
  type FilterChoices,
  type StoryFilters,
} from './stories';

/**
 * The sprint and its stories from GitHub, read-only. Only the filter choice is kept in state;
 * the stories always come straight from the snapshot.
 */
export function StoryBoard({
  sprint,
  stories,
}: {
  sprint: Snapshot['development']['sprint'];
  stories: Story[];
}) {
  const {t, i18n} = useTranslation();
  const [chosen, setChosen] = useState<StoryFilters>(DEFAULT_FILTERS);
  const choices = filterChoices(stories, chosen.currentSprintOnly);
  const filters = effectiveFilters(chosen, choices);
  const progress = sprintProgress(stories);
  const shown = filterStories(stories, filters);

  return (
    <section className="card">
      <div className="card__header story-board__header">
        <div className="item">
          <h2 className="card__title">
            {sprint
              ? t('development.sprintHeading', {
                  name: sprint.name,
                  dates: `${formatDay(sprint.startDate, i18n.language)} – ${formatDay(sprint.endDate, i18n.language)}`,
                })
              : t('development.noSprint')}
          </h2>
          <span className="kpi__note">
            {t('development.storySummary', {
              count: progress.total,
              done: progress.byStatus.done,
              blocked: progress.byStatus.blocked,
            })}
          </span>
        </div>
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
      </div>

      <StoryFilterBar
        choices={choices}
        filters={filters}
        onChange={setChosen}
      />

      {shown.length === 0 ? (
        <p className="item__meta">{t(emptyMessage(filters))}</p>
      ) : (
        <StoryTable stories={shown} />
      )}
    </section>
  );
}

/** "No stories match" only when a filter is set; otherwise the list really is empty. */
function emptyMessage({assignee, label, currentSprintOnly}: StoryFilters) {
  if (assignee !== null || label !== null) {
    return 'development.noMatches';
  }
  return currentSprintOnly
    ? 'development.noStoriesInSprint'
    : 'development.noStories';
}

function StoryFilterBar({
  choices,
  filters,
  onChange,
}: {
  choices: FilterChoices;
  filters: StoryFilters;
  onChange: (filters: StoryFilters) => void;
}) {
  const {t} = useTranslation();
  return (
    <div className="story-filters">
      <label className="field">
        <span className="field__label">{t('development.filterAssignee')}</span>
        <select
          className="field__input"
          value={filters.assignee ?? ''}
          onChange={event =>
            onChange({...filters, assignee: event.target.value || null})
          }
        >
          <option value="">{t('development.filterAll')}</option>
          {choices.assignees.map(name => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        <span className="field__label">{t('development.filterLabel')}</span>
        <select
          className="field__input"
          value={filters.label ?? ''}
          onChange={event =>
            onChange({...filters, label: event.target.value || null})
          }
        >
          <option value="">{t('development.filterAll')}</option>
          {choices.labels.map(label => (
            <option key={label} value={label}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label className="story-filters__check">
        <input
          type="checkbox"
          checked={filters.currentSprintOnly}
          onChange={event =>
            onChange({...filters, currentSprintOnly: event.target.checked})
          }
        />
        {t('development.filterSprint')}
      </label>
    </div>
  );
}

function StoryTable({stories}: {stories: Story[]}) {
  const {t, i18n} = useTranslation();
  const now = useNow();
  return (
    <div className="table-scroll">
      <table className="table">
        <thead>
          <tr>
            <th>{t('development.columnId')}</th>
            <th>{t('development.columnStory')}</th>
            <th>{t('development.columnAssignees')}</th>
            <th>{t('development.columnStatus')}</th>
            <th>{t('development.columnAge')}</th>
          </tr>
        </thead>
        <tbody>
          {stories.map(story => (
            <tr key={story.id}>
              <td className="nowrap mono">#{story.id}</td>
              <td>
                <SourceLink url={story.url}>{story.title}</SourceLink>
              </td>
              <td>
                {story.assignees.join(', ') || t('development.unassigned')}
              </td>
              <td className="table__compact">
                <span
                  className={`pill pill--${STORY_STATUS_TONE[story.status]}`}
                >
                  {t(`storyStatus.${story.status}`)}
                </span>
              </td>
              <td className="nowrap muted">
                {formatAge(story.createdAt, now, i18n.language)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
