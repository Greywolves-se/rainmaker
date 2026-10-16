import {describe, expect, it} from 'vitest';
import type {Story} from '../../data/types';
import {
  assigneesOf,
  effectiveFilters,
  filterChoices,
  filterStories,
  labelsOf,
  sprintProgress,
} from './stories';

const story = (overrides: Partial<Story>): Story => ({
  id: '1',
  title: 'Story',
  assignees: [],
  labels: [],
  status: 'todo',
  inCurrentSprint: true,
  createdAt: '2026-10-20T09:00:00Z',
  url: null,
  ...overrides,
});

const STORIES: Story[] = [
  story({
    id: '101',
    assignees: ['Alex'],
    labels: ['area: web'],
    status: 'inProgress',
  }),
  story({
    id: '102',
    labels: ['area: collector', 'prio: must'],
    status: 'blocked',
  }),
  story({
    id: '97',
    assignees: ['Sam'],
    labels: ['area: collector'],
    status: 'done',
    inCurrentSprint: false,
  }),
  story({
    id: '103',
    assignees: ['Alex', 'Sam'],
    labels: ['area: web'],
    status: 'done',
  }),
];

const ids = (stories: Story[]) => stories.map(s => s.id);
const ALL = {assignee: null, label: null, currentSprintOnly: false};

describe('sprintProgress', () => {
  it('groups the current sprint by status and rounds the percentage', () => {
    expect(sprintProgress(STORIES)).toEqual({
      total: 3,
      byStatus: {todo: 0, inProgress: 1, blocked: 1, done: 1},
      percentDone: 33,
    });
  });

  it('reports 0 % for an empty sprint instead of dividing by zero', () => {
    expect(sprintProgress([]).percentDone).toBe(0);
    expect(sprintProgress([STORIES[2]!]).total).toBe(0);
  });
});

describe('filterStories', () => {
  it('keeps only the current sprint when asked', () => {
    expect(
      ids(filterStories(STORIES, {...ALL, currentSprintOnly: true}))
    ).toEqual(['101', '102', '103']);
    expect(ids(filterStories(STORIES, ALL))).toEqual([
      '101',
      '102',
      '97',
      '103',
    ]);
  });

  it('filters by assignee, including stories with several assignees', () => {
    expect(ids(filterStories(STORIES, {...ALL, assignee: 'Sam'}))).toEqual([
      '97',
      '103',
    ]);
  });

  it('filters by label', () => {
    expect(
      ids(filterStories(STORIES, {...ALL, label: 'area: collector'}))
    ).toEqual(['102', '97']);
  });

  it('combines the filters', () => {
    expect(
      ids(
        filterStories(STORIES, {
          assignee: 'Alex',
          label: 'area: web',
          currentSprintOnly: true,
        })
      )
    ).toEqual(['101', '103']);
    expect(filterStories(STORIES, {...ALL, assignee: 'Nobody'})).toEqual([]);
  });
});

describe('filterChoices and effectiveFilters', () => {
  it('offers only the assignees and labels the sprint toggle allows', () => {
    expect(filterChoices(STORIES, true)).toEqual({
      assignees: ['Alex', 'Sam'],
      labels: ['area: collector', 'area: web', 'prio: must'],
    });
    const sprintOnly = filterChoices([STORIES[2]!], true);
    expect(sprintOnly).toEqual({assignees: [], labels: []});
  });

  it('treats a choice that is no longer offered as "All"', () => {
    const choices = {assignees: ['Alex'], labels: ['area: web']};

    expect(
      effectiveFilters(
        {assignee: 'Sam', label: 'area: web', currentSprintOnly: true},
        choices
      )
    ).toEqual({assignee: null, label: 'area: web', currentSprintOnly: true});
    expect(effectiveFilters({...ALL, label: 'gone'}, choices).label).toBeNull();
  });
});

describe('filter choices', () => {
  it('lists each assignee and label once, sorted', () => {
    expect(assigneesOf(STORIES)).toEqual(['Alex', 'Sam']);
    expect(labelsOf(STORIES)).toEqual([
      'area: collector',
      'area: web',
      'prio: must',
    ]);
  });
});
