/**
 * Shape of the data the pages render. The contract snapshot is mapped to it in fromContract.ts;
 * null means the contract has no data for that part yet and the page shows an empty state.
 */
import type {DocumentEntry, Release, Risk} from '../contract/snapshot';

/** Short code shown in a source tile (GH, GD …). Open-ended, like the contract's sources. */
export type SourceCode = string;

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

/** A document from a document store such as Google Drive (#113): metadata only. */
export interface DocumentItem {
  id: string;
  title: string;
  kind: DocumentEntry['kind'];
  lastEditor: string | null;
  modifiedAt: string;
  url: string | null;
}

/**
 * The latest documents card: `ok` with the newest documents from sources that work, `off` when
 * every document source is switched off (their names), `unavailable` when none works (missing,
 * or failed: the red source banner then says why).
 */
export type LatestDocuments =
  | {status: 'ok'; items: DocumentItem[]}
  | {status: 'off'; sources: string[]}
  | {status: 'unavailable'};

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
    documents: LatestDocuments;
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
  navigator: NavigatorAi;
}

/** Pages that have AI text (#115); START HERE has none. */
export type AiPage = 'overview' | 'development' | 'sources';

/** The AI text for one page in one language. Written by a model: always shown as plain text. */
export interface AiInsight {
  summary: string;
  deviations: string[];
  suggestions: string[];
}

/**
 * The Navigator's AI part (#116, contract README): `off` when the AI step is switched off or the
 * snapshot predates #115 (the part is hidden); `on` otherwise. With `on`, `text` is null when the
 * step failed, and a page is null when only its text failed; `reason` is the step's message.
 */
export type NavigatorAi =
  | {status: 'off'}
  | {
      status: 'on';
      reason: string | null;
      text: {
        model: string;
        generatedAt: string;
        pages: Record<AiPage, {sv: AiInsight; en: AiInsight} | null>;
      } | null;
    };
