// Contract test against the shared files in apps/mission-control/contract (#99).
// Excluded from `tsc` in tsconfig.json, like snapshot.contract.test.ts; Vitest runs it.
import Ajv2020 from 'ajv/dist/2020';
import {describe, expect, it} from 'vitest';
import schema from '../../../contract/projects.schema.json';
import sample from '../../../contract/fixtures/projects.sample.json';
import {parseProjectList, projectListSchema} from './projects';

const ajv = new Ajv2020({allErrors: true, strict: false});
const matchesJsonSchema = ajv.compile(schema);

const clone = () => structuredClone(sample);

describe('projects contract', () => {
  it('sample matches the JSON Schema', () => {
    expect(
      matchesJsonSchema(sample),
      JSON.stringify(matchesJsonSchema.errors)
    ).toBe(true);
  });

  it('reads the sample without losing or changing any field', () => {
    expect(parseProjectList(sample)).toEqual(sample);
  });

  it('rejects an empty list, like the JSON Schema', () => {
    const projects = {...clone(), projects: []};
    expect(matchesJsonSchema(projects)).toBe(false);
    expect(() => parseProjectList(projects)).toThrow(/projects/);
  });

  it('rejects an id that is no folder name, like the JSON Schema', () => {
    const projects = clone();
    projects.projects[0]!.id = '../Rainmaker';
    expect(matchesJsonSchema(projects)).toBe(false);
    expect(() => parseProjectList(projects)).toThrow(/id/);
  });

  it('rejects a missing name, like the JSON Schema', () => {
    const projects = clone();
    delete (projects.projects[0] as Partial<(typeof sample.projects)[0]>).name;
    expect(matchesJsonSchema(projects)).toBe(false);
    expect(() => parseProjectList(projects)).toThrow(/name/);
  });

  it('uses the same id pattern as the JSON Schema', () => {
    const pattern = new RegExp(
      schema.properties.projects.items.properties.id.pattern
    );
    const ids = ['rainmaker', 'mission-control', 'Rainmaker', 'a/b', '-x', ''];
    for (const id of ids) {
      const projects = {schemaVersion: 1, projects: [{id, name: 'Name'}]};
      expect(projectListSchema.safeParse(projects).success, id).toBe(
        pattern.test(id)
      );
    }
  });
});
