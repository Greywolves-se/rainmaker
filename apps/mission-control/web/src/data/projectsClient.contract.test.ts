// Uses the shared contract fixture. Excluded from `tsc` like the other *.contract.test files.
import {describe, expect, it, vi} from 'vitest';
import sample from '../../../contract/fixtures/projects.sample.json';
import {DEMO_PROJECTS, loadProjects} from './projectsClient';
import {SnapshotMissingError} from './snapshotClient';

const respond = (body: unknown, status = 200) =>
  vi.fn().mockResolvedValue(new Response(JSON.stringify(body), {status}));

describe('loadProjects', () => {
  it('reads and checks /projects.json', async () => {
    const fetchImpl = respond(sample);

    const projects = await loadProjects({demoFallback: false, fetchImpl});

    expect(projects.projects.map(p => p.id)).toEqual([
      'rainmaker',
      'mission-control',
    ]);
    expect(fetchImpl).toHaveBeenCalledWith('/projects.json', {
      cache: 'no-store',
    });
  });

  it('uses the demo projects only when allowed and the file is missing', async () => {
    expect(
      await loadProjects({demoFallback: true, fetchImpl: respond({}, 404)})
    ).toBe(DEMO_PROJECTS);
    await expect(
      loadProjects({demoFallback: false, fetchImpl: respond({}, 404)})
    ).rejects.toBeInstanceOf(SnapshotMissingError);
  });

  it('rejects an invalid file and duplicate ids, even with the demo fallback', async () => {
    await expect(
      loadProjects({demoFallback: true, fetchImpl: respond({projects: []})})
    ).rejects.toThrow();
    const duplicate = {
      schemaVersion: 1,
      projects: [sample.projects[0], sample.projects[0]],
    };
    await expect(
      loadProjects({demoFallback: true, fetchImpl: respond(duplicate)})
    ).rejects.toThrow('rainmaker');
  });

  it.each(['assets', 'overview', 'development', 'sources', 'start-here'])(
    'rejects the reserved id "%s", which would clash with a URL the app or nginx uses',
    async id => {
      const reserved = {
        schemaVersion: 1,
        projects: [sample.projects[0], {id, name: 'Clash'}],
      };
      await expect(
        loadProjects({demoFallback: true, fetchImpl: respond(reserved)})
      ).rejects.toThrow(`"${id}"`);
    }
  );

  it('demo projects match the contract sample', () => {
    expect(DEMO_PROJECTS).toEqual(sample);
  });
});
