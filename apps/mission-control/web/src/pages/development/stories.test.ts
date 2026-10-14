import {describe, expect, it} from 'vitest';
import type {Story} from '../../data/types';
import {
  addStory,
  nextStoryId,
  removeStory,
  setStoryStatus,
  storyProgress,
} from './stories';

const STORIES: Story[] = [
  {id: 'MC-01', title: 'First', owner: 'Erik', status: 'done'},
  {id: 'MC-03', title: 'Third', owner: 'Simon', status: 'blocked'},
  {id: 'MC-02', title: 'Second', owner: '', status: 'todo'},
];

describe('nextStoryId', () => {
  it('continues after the highest id, not the count', () => {
    expect(nextStoryId(STORIES)).toBe('MC-04');
  });

  it('starts at MC-01 for an empty list', () => {
    expect(nextStoryId([])).toBe('MC-01');
  });

  it('ignores ids that are not MC-numbers', () => {
    expect(nextStoryId([{...STORIES[0]!, id: 'ABC-99'}])).toBe('MC-01');
  });

  it('never collides with an existing id when there are gaps', () => {
    const withGap = removeStory(STORIES, 'MC-02');
    const ids = withGap.map(story => story.id);

    expect(ids).not.toContain(nextStoryId(withGap));
  });
});

describe('addStory', () => {
  it('appends a trimmed story and leaves the input untouched', () => {
    const result = addStory(STORIES, {
      title: '  New  ',
      owner: ' Mai ',
      status: 'inProgress',
    });

    expect(result).toHaveLength(4);
    expect(result[3]).toEqual({
      id: 'MC-04',
      title: 'New',
      owner: 'Mai',
      status: 'inProgress',
    });
    expect(STORIES).toHaveLength(3);
  });

  it('ignores a blank title', () => {
    expect(
      addStory(STORIES, {title: '   ', owner: 'Mai', status: 'todo'})
    ).toEqual(STORIES);
  });
});

describe('setStoryStatus and removeStory', () => {
  it('changes only the matching story', () => {
    const result = setStoryStatus(STORIES, 'MC-02', 'done');

    expect(result.map(story => story.status)).toEqual([
      'done',
      'blocked',
      'done',
    ]);
  });

  it('leaves the list unchanged for an unknown id', () => {
    expect(setStoryStatus(STORIES, 'MC-99', 'done')).toEqual(STORIES);
    expect(removeStory(STORIES, 'MC-99')).toEqual(STORIES);
  });
});

describe('storyProgress', () => {
  it('counts done and blocked and rounds the percentage', () => {
    expect(storyProgress(STORIES)).toEqual({
      total: 3,
      done: 1,
      blocked: 1,
      percentDone: 33,
    });
  });

  it('reports 0 % for an empty sprint instead of dividing by zero', () => {
    expect(storyProgress([])).toEqual({
      total: 0,
      done: 0,
      blocked: 0,
      percentDone: 0,
    });
  });
});
