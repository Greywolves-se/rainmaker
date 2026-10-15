import type {
  Snapshot as ContractSnapshot,
  Decision,
  Question,
  WorkItem,
} from '../contract/snapshot';
import type {
  Snapshot as PageSnapshot,
  SourceCode,
  StatusCode,
  StoryStatus,
  TitledItem,
} from './types';

/**
 * Turns the domain-shaped contract snapshot into the page-shaped data the components render.
 * This is the only place that knows both shapes. Parts the contract has no data for (KPIs,
 * pilots, AWS cost, Navigator texts …) become null or empty, never invented values.
 */
export function fromContract(snapshot: ContractSnapshot): PageSnapshot {
  const sourceName = new Map(snapshot.sources.map(s => [s.id, s.name]));
  const sprintItems = snapshot.workItems.filter(item => item.inCurrentSprint);
  const byStatus = (status: WorkItem['status']) =>
    sprintItems.filter(item => item.status === status).map(toTitledItem);
  const needOwner = snapshot.decisions.filter(d => d.needsOwnerDecision);
  const sprint = snapshot.sprint;

  return {
    generatedAt: snapshot.generatedAt,
    projects: [{id: 'project', name: snapshot.project.name, health: null}],
    sourceSummary: {
      synced: snapshot.sources.filter(s => s.status === 'ok').length,
      total: snapshot.sources.length,
      note: snapshot.sources.find(s => s.message)?.message ?? null,
    },
    overview: {
      project: null,
      operations: null,
      business: null,
      pilots: null,
      done: byStatus('done'),
      inProgress: byStatus('in_progress'),
      blocked: byStatus('blocked'),
      decisionMakers: [...new Set(needOwner.map(d => d.owner))],
      decisions: needOwner.map(d => ({
        id: d.id,
        title: d.title,
        meta: `${d.owner} · ${d.date}`,
        initial: d.owner.charAt(0),
      })),
      pilotList: [],
      risk: mostSeriousRisk(snapshot.risks),
      awsCost: null,
      release: null,
      events: snapshot.events.map(event => ({
        id: event.id,
        title: event.title,
        meta: sourceName.get(event.source) ?? event.source,
        source: tileCode(event.source),
      })),
    },
    development: {
      sprint: sprint && {
        name: sprint.name,
        dates: `${sprint.startDate} – ${sprint.endDate}`,
      },
      stories: sprintItems.map(item => ({
        id: `#${item.id}`,
        title: item.title,
        owner: item.assignees.join(', '),
        status: STORY_STATUS[item.status],
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
      techDecisions: snapshot.decisions.map(d => ({
        id: d.id,
        title: d.title,
        meta: d.reference ? `${d.owner} · ${d.reference}` : d.owner,
        status: DECISION_STATUS[d.status],
      })),
      questionAudience: null,
      questions: snapshot.questions
        .filter(q => q.status === 'open')
        .map(toQuestion),
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

function toTitledItem(item: WorkItem): TitledItem {
  return {id: item.id, title: item.title, meta: item.assignees.join(', ')};
}

function toQuestion(question: Question) {
  return {
    id: question.id,
    title: question.title,
    meta: question.neededBy ?? '',
    audience: question.owner,
    status: (question.critical ? 'blocker' : 'question') as StatusCode,
  };
}

const RISK_ORDER = {red: 0, yellow: 1, green: 2} as const;

/** The red risk first (then yellow, green), shown in the overview's risk callout. */
function mostSeriousRisk(risks: ContractSnapshot['risks']) {
  const [first] = [...risks].sort(
    (a, b) => RISK_ORDER[a.level] - RISK_ORDER[b.level]
  );
  return first ? {title: first.title, text: first.mitigation} : null;
}
