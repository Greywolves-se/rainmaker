import type {Story, StoryStatus} from '../../data/types';

export const STORY_STATUSES: readonly StoryStatus[] = [
  'todo',
  'inProgress',
  'blocked',
  'done',
];

const ID_PREFIX = 'MC-';

/** Next id after the highest existing `MC-nn` (not the count, so gaps never cause duplicates). */
export function nextStoryId(stories: readonly Story[]): string {
  const highest = stories.reduce((max, story) => {
    const number = Number(story.id.slice(ID_PREFIX.length));
    return story.id.startsWith(ID_PREFIX) && Number.isInteger(number)
      ? Math.max(max, number)
      : max;
  }, 0);
  return ID_PREFIX + String(highest + 1).padStart(2, '0');
}

export interface StoryDraft {
  title: string;
  owner: string;
  status: StoryStatus;
}

/** Returns the stories with the draft appended, or the same list when the title is blank. */
export function addStory(
  stories: readonly Story[],
  draft: StoryDraft
): Story[] {
  const title = draft.title.trim();
  if (!title) {
    return [...stories];
  }
  return [
    ...stories,
    {
      id: nextStoryId(stories),
      title,
      owner: draft.owner.trim(),
      status: draft.status,
    },
  ];
}

export function setStoryStatus(
  stories: readonly Story[],
  id: string,
  status: StoryStatus
): Story[] {
  return stories.map(story => (story.id === id ? {...story, status} : story));
}

export function removeStory(stories: readonly Story[], id: string): Story[] {
  return stories.filter(story => story.id !== id);
}

export interface StoryProgress {
  total: number;
  done: number;
  blocked: number;
  percentDone: number;
}

export function storyProgress(stories: readonly Story[]): StoryProgress {
  const done = stories.filter(story => story.status === 'done').length;
  const blocked = stories.filter(story => story.status === 'blocked').length;
  const percentDone = stories.length
    ? Math.round((done / stories.length) * 100)
    : 0;
  return {total: stories.length, done, blocked, percentDone};
}
