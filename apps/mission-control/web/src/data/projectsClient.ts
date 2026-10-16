import {parseProjectList, type ProjectList} from '../contract/projects';
import {SnapshotMissingError} from './snapshotClient';

/** Written by the collector next to the project folders (#99). */
export const PROJECTS_URL = '/projects.json';

/** The projects the demo shows when the collector has not run (local dev only). */
export const DEMO_PROJECTS: ProjectList = {
  schemaVersion: 1,
  projects: [
    {id: 'rainmaker', name: 'Rainmaker'},
    {id: 'mission-control', name: 'Mission Control'},
  ],
};

/**
 * Loads and checks projects.json. With demoFallback (local dev only) a missing file gives the
 * demo projects; an invalid file, or two projects with the same id, always fails.
 */
export async function loadProjects({
  demoFallback,
  fetchImpl = fetch,
}: {
  demoFallback: boolean;
  fetchImpl?: typeof fetch;
}): Promise<ProjectList> {
  let response: Response;
  try {
    response = await fetchImpl(PROJECTS_URL, {cache: 'no-store'});
  } catch (error) {
    if (demoFallback) {
      return DEMO_PROJECTS;
    }
    throw new SnapshotMissingError(`Could not reach ${PROJECTS_URL}`, {
      cause: error,
    });
  }
  if (response.status === 404) {
    if (demoFallback) {
      return DEMO_PROJECTS;
    }
    throw new SnapshotMissingError(`${PROJECTS_URL} was not found`);
  }
  if (!response.ok) {
    throw new Error(`${PROJECTS_URL} returned HTTP ${response.status}`);
  }
  const list = parseProjectList(await response.json());
  const ids = list.projects.map(project => project.id);
  const duplicate = ids.find((id, index) => ids.indexOf(id) !== index);
  if (duplicate) {
    // The schema cannot say ids are unique (contract README), so the web app checks it.
    throw new Error(`${PROJECTS_URL} lists the project "${duplicate}" twice`);
  }
  return list;
}
