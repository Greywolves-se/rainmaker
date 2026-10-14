/** Shape of the data the dashboard renders. Mirrors the mockup until the snapshot contract (#10) exists. */

export type SourceCode = 'GD' | 'GH' | 'SL' | 'AWS' | 'RM' | 'FI';

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
  | 'question';

export type StoryStatus = 'todo' | 'inProgress' | 'blocked' | 'done';

export interface Story {
  id: string;
  title: string;
  owner: string;
  status: StoryStatus;
}

export interface TitledItem {
  title: string;
  meta: string;
}

export interface StatusItem extends TitledItem {
  status: StatusCode;
}

export interface SourceEvent extends TitledItem {
  source: SourceCode;
}

export interface Snapshot {
  generatedAt: string;
  projects: {name: string; health: Health}[];
  sourceSummary: {synced: number; total: number; note: string};
  overview: {
    project: {status: StatusCode; sprint: string; percentDone: number};
    operations: {status: StatusCode; note: string};
    business: {status: StatusCode; note: string};
    pilots: {active: number; note: string};
    done: TitledItem[];
    inProgress: TitledItem[];
    blocked: TitledItem[];
    decisionMakers: string[];
    decisions: (TitledItem & {initial: string})[];
    pilotList: StatusItem[];
    risk: {title: string; text: string};
    awsCost: {month: string; amountSek: number | null};
    release: {version: string; badge: string; notes: string};
    events: SourceEvent[];
  };
  development: {
    sprint: {name: string; dates: string};
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
    };
    techDecisions: StatusItem[];
    questionAudience: string;
    questions: (StatusItem & {audience: string})[];
    links: TitledItem[];
  };
  sources: {
    cards: {
      code: SourceCode;
      name: string;
      account: string;
      role: string;
      tags: string[];
      scope: string;
      mapsTo: string;
      updates: string;
      status: StatusCode;
      lastSync: string;
    }[];
    mapping: {concept: string; from: string; example: string}[];
    syncLog: {
      time: string;
      source: SourceCode;
      text: string;
      status: StatusCode;
    }[];
  };
  navigator: {
    summaryTime: string;
    summary: string;
    projects: {title: string; health: Health; text: string}[];
    nextSteps: string[];
    questions: {question: string; answer: string}[];
  };
}
