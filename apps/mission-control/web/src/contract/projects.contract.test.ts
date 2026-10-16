// Contract test against the shared files in apps/mission-control/contract (#99).
// Excluded from `tsc` in tsconfig.json, like snapshot.contract.test.ts; Vitest runs it.
import Ajv2020 from 'ajv/dist/2020';
import {describe, expect, it} from 'vitest';
import schema from '../../../contract/projects.schema.json';
import sample from '../../../contract/fixtures/projects.sample.json';
import {
  parseProjectList,
  projectIdMaxLength,
  projectIdPattern,
  projectListSchema,
} from './projects';

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

  it('uses the same id pattern and length limit as the JSON Schema', () => {
    const idSchema = schema.properties.projects.items.properties.id;
    expect(projectIdPattern.source).toBe(idSchema.pattern);
    expect(projectIdMaxLength).toBe(idSchema.maxLength);
  });

  it('accepts and rejects the same ids as the JSON Schema', () => {
    const ids = [
      'rainmaker',
      'mission-control',
      'a'.repeat(40),
      'a'.repeat(41),
      'Rainmaker',
      'a/b',
      '-x',
      '',
    ];
    for (const id of ids) {
      const projects = {schemaVersion: 1, projects: [{id, name: 'Name'}]};
      expect(projectListSchema.safeParse(projects).success, id).toBe(
        matchesJsonSchema(projects)
      );
    }
  });

  it('rejects an id used twice, which the JSON Schema cannot check', () => {
    const projects = clone();
    projects.projects[1]!.id = 'rainmaker';
    expect(() => parseProjectList(projects)).toThrow(/used twice/);
  });

  it('drops unknown fields on purpose, where the JSON Schema rejects them', () => {
    const projects = clone();
    const withColour = {...projects.projects[0]!, colour: 'blue'};
    const json = {...projects, projects: [withColour], costSek: 1200};
    expect(matchesJsonSchema(json)).toBe(false);
    const parsed = parseProjectList(json);
    expect(parsed).not.toHaveProperty('costSek');
    expect(parsed.projects[0]).not.toHaveProperty('colour');
  });
});
