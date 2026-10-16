/**
 * Shape of the data the pages render. The contract snapshot is mapped to it in fromContract.ts;
 * null means the contract has no data for that part yet and the page shows an empty state.
 */
import type {Release, Risk} from '../contract/snapshot';

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
  | 'disabled'
  | 'overdue'
  | 'riskRed'
  | 'riskYellow'
  | 'riskGreen';

export type StoryStatus = 'todo' | 'inProgress' | 'blocked' | 'done';

/** A GitHub work item on the Development page (read-only). */
export interface Story {
  /** The issue number, e.g. "101". */
  id: string;
  title: string;
  assignees: string[];
  labels: string[];
  status: StoryStatus;
  inCurrentSprint: boolean;
  createdAt: string;
  url: string | null;
}

/** Every list item has an id so React keys stay unique when titles repeat. */
export interface TitledItem {
  id: string;
  title: string;
  meta: string;
}

/** A decision from decisions.yaml; url is the line in the file on GitHub. */
export interface DecisionItem {
  id: string;
  title: string;
  owner: string;
  /** YYYY-MM-DD */
  date: string;
  status: StatusCode;
  /** Free text such as "Section 6; D17", not a link. */
  reference: string | null;
  url: string | null;
}

export interface StatusItem extends TitledItem {
  status: StatusCode;
}

/** A risk on the overview, with its owner and what is done about it. */
export interface RiskItem {
  id: string;
  title: string;
  level: Risk['level'];
  owner: string;
  mitigation: string;
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
    /** Decisions waiting for the owners, oldest first. */
    decisions: DecisionItem[];
    pilotList: StatusItem[];
    /** Every risk, red first (then yellow, green). */
    risks: RiskItem[];
    awsCost: {month: string; amountSek: number | null} | null;
    release: {
      version: string;
      /** The contract's values (dev, uat, prod), so a new one fails the type check. */
      environment: Release['environment'];
      publishedAt: string;
      url: string | null;
    } | null;
    /** Newest first. */
    events: SourceEvent[];
  };
  development: {
    sprint: {
      name: string;
      startDate: string;
      endDate: string;
      url: string | null;
    } | null;
    /** All work items, so the "only current sprint" filter can be switched off. */
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
    /** Every decision not waiting for the owners, in log order. */
    techDecisions: DecisionItem[];
    questionAudience: string | null;
    /** Open questions, nearest needed-by date first. */
    questions: (StatusItem & {audience: string; neededBy: string | null})[];
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
