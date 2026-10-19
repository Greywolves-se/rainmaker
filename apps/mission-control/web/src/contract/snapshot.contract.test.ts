// Contract test against the shared files in apps/mission-control/contract.
// Excluded from `tsc` in tsconfig.json: the Docker build copies only web/, so these imports
// would not resolve there. Vitest runs it locally and in CI.
import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import {describe, expect, it} from 'vitest';
import schema from '../../../contract/snapshot.schema.json';
import sample from '../../../contract/fixtures/snapshot.sample.json';
import {
  buildStatus,
  decisionStatus,
  documentKind,
  linkCategory,
  parseSnapshot,
  pullRequestChecks,
  pullRequestReview,
  pullRequestState,
  questionStatus,
  releaseEnvironment,
  riskLevel,
  sourceStatus,
  workItemStatus,
} from './snapshot';

const ajv = new Ajv2020({allErrors: true, strict: false});
addFormats(ajv);
const matchesJsonSchema = ajv.compile(schema);

const clone = () =>
  structuredClone(sample) as Record<string, unknown> & typeof sample;

describe('snapshot contract', () => {
  it('sample matches the JSON Schema', () => {
    expect(
      matchesJsonSchema(sample),
      JSON.stringify(matchesJsonSchema.errors)
    ).toBe(true);
  });

  it('reads the sample without losing or changing any field', () => {
    expect(parseSnapshot(sample)).toEqual(sample);
  });

  it('rejects a missing field', () => {
    const snapshot = clone();
    delete (snapshot.workItems[0] as Partial<(typeof sample.workItems)[0]>)
      .closedAt;
    expect(() => parseSnapshot(snapshot)).toThrow(/closedAt/);
  });

  it('rejects an unknown status value', () => {
    const snapshot = clone();
    snapshot.workItems[0]!.status = 'doing';
    expect(() => parseSnapshot(snapshot)).toThrow(/status/);
  });

  it('drops fields it does not know yet instead of failing', () => {
    const snapshot = {...clone(), costSek: 1200};
    expect(parseSnapshot(snapshot)).not.toHaveProperty('costSek');
  });

  it.each([
    {definition: 'source', field: 'status', zodEnum: sourceStatus},
    {definition: 'workItem', field: 'status', zodEnum: workItemStatus},
    {definition: 'pullRequest', field: 'state', zodEnum: pullRequestState},
    {definition: 'pullRequest', field: 'review', zodEnum: pullRequestReview},
    {definition: 'pullRequest', field: 'checks', zodEnum: pullRequestChecks},
    {definition: 'build', field: 'status', zodEnum: buildStatus},
    {definition: 'release', field: 'environment', zodEnum: releaseEnvironment},
    {definition: 'decision', field: 'status', zodEnum: decisionStatus},
    {definition: 'risk', field: 'level', zodEnum: riskLevel},
    {definition: 'question', field: 'status', zodEnum: questionStatus},
    {definition: 'link', field: 'category', zodEnum: linkCategory},
    {definition: 'document', field: 'kind', zodEnum: documentKind},
  ])(
    '$definition.$field has exactly the schema values',
    ({definition, field, zodEnum}) => {
      const definitions = schema.$defs as Record<
        string,
        {properties: Record<string, {enum?: string[]}>}
      >;
      const schemaValues = definitions[definition]?.properties[field]?.enum;
      expect([...zodEnum.options].sort()).toEqual(
        [...(schemaValues ?? [])].sort()
      );
    }
  );
});
