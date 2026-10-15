import type {Story, StoryStatus} from '../../data/types';

export const STORY_STATUSES: readonly StoryStatus[] = [
  'todo',
  'inProgress',
  'blocked',
  'done',
];

export interface StoryFilters {
  /** Only stories this person is assigned to; null = everyone. */
  assignee: string | null;
  /** Only stories with this label; null = any label. */
  label: string | null;
  currentSprintOnly: boolean;
}

export const DEFAULT_FILTERS: StoryFilters = {
  assignee: null,
  label: null,
  currentSprintOnly: true,
};

export function filterStories(
  stories: readonly Story[],
  {assignee, label, currentSprintOnly}: StoryFilters
): Story[] {
  return stories.filter(
    story =>
      (!currentSprintOnly || story.inCurrentSprint) &&
      (assignee === null || story.assignees.includes(assignee)) &&
      (label === null || story.labels.includes(label))
  );
}

export interface SprintProgress {
  total: number;
  byStatus: Record<StoryStatus, number>;
  percentDone: number;
}

/** Progress of the current sprint, whatever the filters show. */
export function sprintProgress(stories: readonly Story[]): SprintProgress {
  const sprint = stories.filter(story => story.inCurrentSprint);
  const byStatus = {todo: 0, inProgress: 0, blocked: 0, done: 0};
  for (const story of sprint) {
    byStatus[story.status] += 1;
  }
  const percentDone = sprint.length
    ? Math.round((byStatus.done / sprint.length) * 100)
    : 0;
  return {total: sprint.length, byStatus, percentDone};
}

/** The choices for the assignee filter: each person once, sorted. */
export function assigneesOf(stories: readonly Story[]): string[] {
  return [...new Set(stories.flatMap(story => story.assignees))].sort();
}

/** The choices for the label filter: each label once, sorted. */
export function labelsOf(stories: readonly Story[]): string[] {
  return [...new Set(stories.flatMap(story => story.labels))].sort();
}
