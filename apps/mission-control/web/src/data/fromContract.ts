import type {
  Snapshot as ContractSnapshot,
  Decision,
  Question,
  WorkItem,
} from '../contract/snapshot';
import type {
  DecisionItem,
  LatestDocuments,
  LinkedItem,
  Snapshot as PageSnapshot,
  SourceCode,
  StatusCode,
  StoryStatus,
} from './types';
import {sortQuestions, sortRisks} from './risksAndQuestions';

/**
 * Turns the domain-shaped contract snapshot into the page-shaped data the components render.
 * This is the only place that knows both shapes. Parts the contract has no data for (pilots,
 * AWS cost, Navigator texts …) become null or empty, never invented values.
 */
export function fromContract(snapshot: ContractSnapshot): PageSnapshot {
  const sourceName = new Map(snapshot.sources.map(s => [s.id, s.name]));
  const sprintItems = snapshot.workItems.filter(item => item.inCurrentSprint);
  const byStatus = (status: WorkItem['status']) =>
    sprintItems.filter(item => item.status === status).map(toLinkedItem);
  const waiting = snapshot.decisions
    .filter(isWaitingForOwner)
    .sort((a, b) => Date.parse(a.date) - Date.parse(b.date));
  const sprint = snapshot.sprint;

  return {
    generatedAt: snapshot.generatedAt,
    sourceSummary: {
      synced: snapshot.sources.filter(s => s.status === 'ok').length,
      total: snapshot.sources.length,
      note: snapshot.sources.find(s => s.message)?.message ?? null,
    },
    overview: {
      sprint: sprint && {
        name: sprint.name,
        done: sprintItems.filter(item => item.status === 'done').length,
        total: sprintItems.length,
        url: sprint.url,
      },
      milestones: snapshot.project.milestones,
      done: byStatus('done'),
      inProgress: byStatus('in_progress'),
      blocked: byStatus('blocked'),
      decisionMakers: [...new Set(waiting.map(d => d.owner))],
      decisions: waiting.map(toDecisionItem),
      pilotList: [],
      risks: sortRisks(snapshot.risks).map(risk => ({
        id: risk.id,
        title: risk.title,
        level: risk.level,
        owner: risk.owner,
        mitigation: risk.mitigation,
      })),
      awsCost: null,
      release: latestRelease(snapshot.releases),
      events: [...snapshot.events]
        .sort((a, b) => newestFirst(a.occurredAt, b.occurredAt))
        .map(event => ({
          id: event.id,
          title: event.title,
          source: tileCode(event.source),
          sourceName: sourceName.get(event.source) ?? event.source,
          occurredAt: event.occurredAt,
          url: event.url,
        })),
      documents: latestDocuments(snapshot),
    },
    development: {
      sprint: sprint && {
        name: sprint.name,
        startDate: sprint.startDate,
        endDate: sprint.endDate,
        url: sprint.url,
      },
      stories: snapshot.workItems.map(item => ({
        id: item.id,
        title: item.title,
        assignees: item.assignees,
        labels: item.labels,
        status: STORY_STATUS[item.status],
        inCurrentSprint: item.inCurrentSprint,
        createdAt: item.createdAt,
        url: item.url,
      })),
      pullRequests: snapshot.pullRequests
        .filter(pr => pr.state !== 'closed')
        .map(pr => ({
          id: pr.id,
          number: `#${pr.number}`,
          title: pr.title,
          meta: pr.author,
          status: pr.state === 'merged' ? 'merged' : 'review',
        })),
      build: null,
      techDecisions: snapshot.decisions
        .filter(d => !isWaitingForOwner(d))
        .map(toDecisionItem),
      questionAudience: null,
      questions: sortQuestions(
        snapshot.questions.filter(q => q.status === 'open')
      ).map(toQuestion),
      links: snapshot.links.map(link => ({
        id: link.id,
        title: link.title,
        meta: new URL(link.url).host,
      })),
    },
    sources: {
      cards: snapshot.sources.map(source => ({
        code: tileCode(source.id),
        name: source.name,
        account: null,
        role: null,
        tags: [],
        scope: null,
        mapsTo: null,
        updates: null,
        status: SOURCE_STATUS[source.status],
        lastSync: source.message,
      })),
      mapping: [],
      syncLog: [],
    },
    navigator: {
      summaryTime: null,
      summary: null,
      projects: [],
      nextSteps: [],
      questions: [],
    },
  };
}

const STORY_STATUS: Record<WorkItem['status'], StoryStatus> = {
  todo: 'todo',
  in_progress: 'inProgress',
  blocked: 'blocked',
  done: 'done',
};

/** Peter or Staffan still has to decide: it needs them and is not decided or replaced yet. */
function isWaitingForOwner(decision: Decision): boolean {
  return decision.needsOwnerDecision && decision.status === 'proposed';
}

function toDecisionItem(decision: Decision): DecisionItem {
  return {
    id: decision.id,
    title: decision.title,
    owner: decision.owner,
    date: decision.date,
    status: DECISION_STATUS[decision.status],
    reference: decision.reference,
    url: decision.url,
  };
}

const DECISION_STATUS: Record<Decision['status'], StatusCode> = {
  proposed: 'proposed',
  decided: 'decided',
  superseded: 'superseded',
};

const SOURCE_STATUS: Record<
  ContractSnapshot['sources'][number]['status'],
  StatusCode
> = {
  ok: 'synced',
  error: 'error',
  disabled: 'disabled',
};

/** Tile codes the mockup styles; any other source gets its first two letters. */
const KNOWN_TILE_CODES: Record<string, SourceCode> = {
  github: 'GH',
  'google-drive': 'GD',
  slack: 'SL',
  aws: 'AWS',
};

function tileCode(sourceId: string): SourceCode {
  return KNOWN_TILE_CODES[sourceId] ?? sourceId.slice(0, 2).toUpperCase();
}

function toLinkedItem(item: WorkItem): LinkedItem {
  return {
    id: item.id,
    title: item.title,
    meta: item.assignees.join(', '),
    url: item.url,
  };
}

/** Compares timestamps by time, so "…30.5Z" and "…30Z" sort correctly (text order would not). */
function newestFirst(a: string, b: string): number {
  return Date.parse(b) - Date.parse(a);
}

/** How many documents the Overview lists (#114 asks for 5–10). */
const MAX_DOCUMENTS = 8;

/** Sources that provide documents even when they have none yet (#113). */
const KNOWN_DOCUMENT_SOURCES = ['google-drive'];

/**
 * The most recently changed documents, newest first, from the document sources that work. A
 * document whose own source failed is left out, so one failing store never hides another.
 */
function latestDocuments(snapshot: ContractSnapshot): LatestDocuments {
  const documentSourceIds = new Set([
    ...KNOWN_DOCUMENT_SOURCES,
    ...snapshot.documents.map(entry => entry.source),
  ]);
  const documentSources = snapshot.sources.filter(source =>
    documentSourceIds.has(source.id)
  );
  const working = new Set(
    documentSources.filter(s => s.status === 'ok').map(s => s.id)
  );
  if (working.size === 0) {
    const allOff =
      documentSources.length > 0 &&
      documentSources.every(source => source.status === 'disabled');
    return allOff
      ? {status: 'off', sources: documentSources.map(source => source.name)}
      : {status: 'unavailable'};
  }
  const items = snapshot.documents
    .filter(entry => working.has(entry.source))
    .sort((a, b) => newestFirst(a.modifiedAt, b.modifiedAt))
    .slice(0, MAX_DOCUMENTS)
    .map(entry => ({
      id: entry.id,
      title: entry.title,
      kind: entry.kind,
      lastEditor: entry.lastEditor,
      modifiedAt: entry.modifiedAt,
      url: entry.url,
    }));
  return {status: 'ok', items};
}

/** The most recently published release, in any environment. */
function latestRelease(releases: ContractSnapshot['releases']) {
  const [latest] = [...releases].sort((a, b) =>
    newestFirst(a.publishedAt, b.publishedAt)
  );
  return latest
    ? {
        version: latest.version,
        environment: latest.environment,
        publishedAt: latest.publishedAt,
        url: latest.url,
      }
    : null;
}

function toQuestion(question: Question) {
  return {
    id: question.id,
    title: question.title,
    meta: '',
    audience: question.owner,
    neededBy: question.neededBy,
    status: (question.critical ? 'blocker' : 'question') as StatusCode,
  };
}
