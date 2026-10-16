import {parseProjectList, type ProjectList} from '../contract/projects';
import {PAGES} from '../layout/pages';
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
  const reserved = ids.find(id => RESERVED_PROJECT_IDS.has(id));
  if (reserved) {
    throw new Error(
      `${PROJECTS_URL} uses the reserved project id "${reserved}"; its pages would not open`
    );
  }
  return list;
}

/**
 * Ids that are already a URL of their own: the old page routes (/development …, redirected to a
 * project) and nginx's /assets/ (the app's static files). A project with one of these ids could
 * never be opened, so the list is rejected instead of failing quietly.
 */
const RESERVED_PROJECT_IDS: ReadonlySet<string> = new Set([
  'assets',
  ...PAGES.map(page => page.segment),
]);
