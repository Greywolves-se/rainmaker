/**
 * Shape of the data the pages render. The contract snapshot is mapped to it in fromContract.ts;
 * null means the contract has no data for that part yet and the page shows an empty state.
 */

/** Short code shown in a source tile (GH, GD …). Open-ended, like the contract's sources. */
export type SourceCode = string;

export type Health = 'green' | 'yellow';

/** Status codes shown as pills. Labels live in the translation files under `status`. */
export type StatusCode =
  | 'onTrack'
  | 'stable'
  | 'growing'
  | 'running'
  | 'atRisk'
  | 'review'
  | 'merged'
  | 'decided'
  | 'proposed'
  | 'missing'
  | 'done'
  | 'synced'
  | 'partial'
  | 'ok'
  | 'error'
  | 'blocker'
  | 'question'
  | 'superseded'
  | 'disabled';

export type StoryStatus = 'todo' | 'inProgress' | 'blocked' | 'done';

export interface Story {
  id: string;
  title: string;
  owner: string;
  status: StoryStatus;
}

/** Every list item has an id so React keys stay unique when titles repeat. */
export interface TitledItem {
  id: string;
  title: string;
  meta: string;
}

export interface StatusItem extends TitledItem {
  status: StatusCode;
}

/** An item that links to its details (a GitHub issue, a pull request …); url is null without one. */
export interface LinkedItem extends TitledItem {
  url: string | null;
}

export interface SourceEvent {
  id: string;
  title: string;
  source: SourceCode;
  sourceName: string;
  occurredAt: string;
  url: string | null;
}

export interface Snapshot {
  generatedAt: string;
  projects: {id: string; name: string; health: Health | null}[];
  sourceSummary: {synced: number; total: number; note: string | null};
  overview: {
    sprint: {
      name: string;
      done: number;
      total: number;
      url: string | null;
    } | null;
    /** Key dates (Goal 1, pilot start), shown as countdowns. */
    milestones: {id: string; title: string; date: string}[];
    done: LinkedItem[];
    inProgress: LinkedItem[];
    blocked: LinkedItem[];
    decisionMakers: string[];
    decisions: (TitledItem & {initial: string})[];
    pilotList: StatusItem[];
    risk: {title: string; text: string} | null;
    awsCost: {month: string; amountSek: number | null} | null;
    release: {
      version: string;
      environment: string;
      publishedAt: string;
      url: string | null;
    } | null;
    /** Newest first. */
    events: SourceEvent[];
  };
  development: {
    sprint: {name: string; dates: string} | null;
    stories: Story[];
    pullRequests: (StatusItem & {number: string})[];
    build: {
      branch: string;
      number: number;
      age: string;
      duration: string;
      unitTests: string;
      lint: string;
      staging: string;
      steps: string[];
    } | null;
    techDecisions: StatusItem[];
    questionAudience: string | null;
    questions: (StatusItem & {audience: string})[];
    links: TitledItem[];
  };
  sources: {
    cards: {
      code: SourceCode;
      name: string;
      account: string | null;
      role: string | null;
      tags: string[];
      scope: string | null;
      mapsTo: string | null;
      updates: string | null;
      status: StatusCode;
      lastSync: string | null;
    }[];
    mapping: {id: string; concept: string; from: string; example: string}[];
    syncLog: {
      id: string;
      time: string;
      source: SourceCode;
      text: string;
      status: StatusCode;
    }[];
  };
  navigator: {
    summaryTime: string | null;
    summary: string | null;
    projects: {id: string; title: string; health: Health; text: string}[];
    nextSteps: string[];
    questions: {id: string; question: string; answer: string}[];
  };
}
