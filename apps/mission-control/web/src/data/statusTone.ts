import type {StatusCode, StoryStatus} from './types';

export type Tone = 'green' | 'blue' | 'red' | 'amber' | 'grey';

export const STATUS_TONE: Record<StatusCode, Tone> = {
  onTrack: 'green',
  stable: 'green',
  growing: 'blue',
  running: 'green',
  atRisk: 'amber',
  review: 'amber',
  merged: 'green',
  decided: 'green',
  proposed: 'blue',
  missing: 'red',
  done: 'green',
  synced: 'green',
  partial: 'amber',
  ok: 'green',
  error: 'red',
  blocker: 'red',
  question: 'blue',
  superseded: 'grey',
  disabled: 'grey',
  overdue: 'red',
  riskRed: 'red',
  riskYellow: 'amber',
  riskGreen: 'green',
};

export const STORY_STATUS_TONE: Record<StoryStatus, Tone> = {
  todo: 'grey',
  inProgress: 'blue',
  blocked: 'red',
  done: 'green',
};
