/**
 * TypeScript form of the Mission Control snapshot contract
 * (apps/mission-control/contract/snapshot.schema.json). The zod schemas mirror the JSON Schema
 * field by field; the types are derived from them, so there is no second hand-written copy.
 * Fields that can be null are `.nullable()`; every field is required.
 */
import {z} from 'zod';

const text = z.string().min(1);
const id = z.string().min(1);
const sourceId = z.string().regex(/^[a-z][a-z0-9-]*$/);
const person = z.string().min(1);
const timestamp = z.iso.datetime();
const date = z.iso.date();
const url = z.url();
const fromSource = {source: sourceId, url};

export const sourceStatus = z.enum(['ok', 'error', 'disabled']);
export const workItemStatus = z.enum([
  'todo',
  'in_progress',
  'blocked',
  'done',
]);
export const pullRequestState = z.enum(['open', 'merged', 'closed']);
export const pullRequestReview = z.enum([
  'pending',
  'approved',
  'changes_requested',
]);
export const pullRequestChecks = z.enum([
  'passing',
  'failing',
  'pending',
  'none',
]);
export const buildStatus = z.enum([
  'success',
  'failure',
  'running',
  'cancelled',
]);
export const releaseEnvironment = z.enum(['dev', 'uat', 'prod']);
export const decisionStatus = z.enum(['proposed', 'decided', 'superseded']);
export const riskLevel = z.enum(['green', 'yellow', 'red']);
export const questionStatus = z.enum(['open', 'answered']);
export const linkCategory = z.enum([
  'environment',
  'dashboard',
  'document',
  'other',
]);

export const documentKind = z.enum([
  'document',
  'spreadsheet',
  'presentation',
  'pdf',
  'other',
]);

const milestone = z.object({id, title: text, date});

const source = z.object({
  id: sourceId,
  name: text,
  status: sourceStatus,
  fetchedAt: timestamp.nullable(),
  message: text.nullable(),
});

const sprint = z.object({
  name: text,
  startDate: date,
  endDate: date,
  goal: text.nullable(),
  ...fromSource,
});

const workItem = z.object({
  id,
  title: text,
  status: workItemStatus,
  assignees: z.array(person),
  labels: z.array(text),
  inCurrentSprint: z.boolean(),
  createdAt: timestamp,
  closedAt: timestamp.nullable(),
  ...fromSource,
});

const pullRequest = z.object({
  id,
  number: z.number().int().min(1),
  title: text,
  author: person,
  draft: z.boolean(),
  state: pullRequestState,
  review: pullRequestReview,
  checks: pullRequestChecks,
  createdAt: timestamp,
  mergedAt: timestamp.nullable(),
  ...fromSource,
});

const build = z.object({
  id,
  workflow: text,
  branch: text,
  status: buildStatus,
  startedAt: timestamp,
  finishedAt: timestamp.nullable(),
  ...fromSource,
});

const release = z.object({
  id,
  version: text,
  environment: releaseEnvironment,
  publishedAt: timestamp,
  ...fromSource,
});

const decision = z.object({
  id,
  title: text,
  owner: person,
  date,
  status: decisionStatus,
  needsOwnerDecision: z.boolean(),
  reference: text.nullable(),
  ...fromSource,
});

const risk = z.object({
  id,
  title: text,
  level: riskLevel,
  owner: person,
  mitigation: text,
  ...fromSource,
});

const question = z.object({
  id,
  title: text,
  owner: person,
  neededBy: date.nullable(),
  status: questionStatus,
  critical: z.boolean(),
  ...fromSource,
});

const link = z.object({id, title: text, category: linkCategory, ...fromSource});

const change = z.object({
  id,
  date,
  title: text,
  owner: person,
  rollbackPlan: text,
  ...fromSource,
});

const event = z.object({id, occurredAt: timestamp, title: text, ...fromSource});

// Named documentItem, not document, so it never hides the browser's global document.
const documentItem = z.object({
  id,
  title: text,
  kind: documentKind,
  lastEditor: person.nullable(),
  modifiedAt: timestamp,
  ...fromSource,
});

// The Navigator's AI text (#115, ADR 0002): written by an AI model, so show it as plain text
// and label it as AI. Limits match the JSON Schema, which counts characters as code points:
// an emoji is one character there but two in a JavaScript string's length.
const aiText = (max: number) =>
  z
    .string()
    .min(1)
    .refine(text => [...text].length <= max, {
      message: `At most ${max} characters`,
    });
const insight = z.object({
  summary: aiText(600),
  deviations: z.array(aiText(300)).max(5),
  suggestions: z.array(aiText(300)).max(5),
});
const pageInsight = z.object({sv: insight, en: insight});
// Named navigatorText, not navigator, so it never hides the browser's global navigator.
const navigatorText = z.object({
  generatedAt: timestamp,
  model: text,
  pages: z.object({
    overview: pageInsight.nullable(),
    development: pageInsight.nullable(),
    sources: pageInsight.nullable(),
  }),
});

/**
 * The whole snapshot. Fields the web app does not know yet are dropped instead of rejected, so a
 * newer collector never breaks an older dashboard.
 */
export const snapshotSchema = z.object({
  schemaVersion: z.literal(1),
  generatedAt: timestamp,
  project: z.object({name: text, milestones: z.array(milestone)}),
  sources: z.array(source),
  sprint: sprint.nullable(),
  workItems: z.array(workItem),
  pullRequests: z.array(pullRequest),
  builds: z.array(build),
  releases: z.array(release),
  decisions: z.array(decision),
  risks: z.array(risk),
  questions: z.array(question),
  links: z.array(link),
  changes: z.array(change),
  events: z.array(event),
  // Lenient on purpose: a snapshot written before #113 has no documents list; read it as empty.
  documents: z.array(documentItem).default([]),
  // Lenient on purpose: a snapshot written before #115 has no navigator (read as none), and a
  // broken navigator only costs the panel, never the rest of the dashboard (#134 review).
  navigator: navigatorText
    .nullable()
    .default(null)
    .catch(context => {
      // Only the failing paths are logged, never the AI text itself.
      console.warn(
        'Navigator AI text does not match the contract and is ignored:',
        context.error.issues.map(issue => issue.path.join('.'))
      );
      return null;
    }),
});

export type Snapshot = z.infer<typeof snapshotSchema>;
export type Source = Snapshot['sources'][number];
export type WorkItem = Snapshot['workItems'][number];
export type PullRequest = Snapshot['pullRequests'][number];
export type Build = Snapshot['builds'][number];
export type Release = Snapshot['releases'][number];
export type Decision = Snapshot['decisions'][number];
export type Risk = Snapshot['risks'][number];
export type Question = Snapshot['questions'][number];
export type Link = Snapshot['links'][number];
export type Change = Snapshot['changes'][number];
export type Event = Snapshot['events'][number];
export type DocumentEntry = Snapshot['documents'][number];
export type NavigatorText = NonNullable<Snapshot['navigator']>;
export type PageInsight = NonNullable<NavigatorText['pages']['overview']>;

/**
 * Checks untrusted JSON (e.g. a fetched snapshot.json) against the contract.
 * Throws a `ZodError` that names the failing fields when it does not match.
 */
export function parseSnapshot(json: unknown): Snapshot {
  return snapshotSchema.parse(json);
}
