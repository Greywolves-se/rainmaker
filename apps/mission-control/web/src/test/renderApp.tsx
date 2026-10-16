import {render} from '@testing-library/react';
import {I18nextProvider} from 'react-i18next';
import {MemoryRouter} from 'react-router';
import {App} from '../App';
import type {ProjectList} from '../contract/projects';
import {DataSourceProvider, type DataSource} from '../data/dataSource';
import {DEMO_PROJECTS} from '../data/projectsClient';
import {DEMO_SNAPSHOT, type LoadedSnapshot} from '../data/snapshotClient';
import {createI18n} from '../i18n/i18n';

/** The two projects of the contract sample: Rainmaker and Mission Control. */
export const TEST_PROJECTS: ProjectList = DEMO_PROJECTS;

function renderWithSource(path: string, source: DataSource) {
  return render(
    <I18nextProvider i18n={createI18n('en')}>
      <MemoryRouter initialEntries={[path]}>
        <DataSourceProvider source={source}>
          <App />
        </DataSourceProvider>
      </MemoryRouter>
    </I18nextProvider>
  );
}

/**
 * Renders the whole app at `path` with its own i18n instance, so tests never share state.
 * The data is already loaded: the demo sample for every project unless `loaded` is given.
 */
export function renderApp({
  path = '/',
  loaded = DEMO_SNAPSHOT,
  projects = TEST_PROJECTS,
}: {
  path?: string;
  loaded?: LoadedSnapshot | ((projectId: string) => LoadedSnapshot);
  projects?: ProjectList;
} = {}) {
  const snapshotFor = typeof loaded === 'function' ? loaded : () => loaded;
  return renderWithSource(path, {
    projects: () => Promise.resolve(projects),
    snapshot: projectId => Promise.resolve(snapshotFor(projectId)),
    preloaded: {projects, snapshot: snapshotFor},
  });
}

/** Renders the whole app the way main.tsx does: each project's snapshot is loaded by `snapshot`. */
export function renderAppWithSource({
  path = '/',
  snapshot,
}: {
  path?: string;
  snapshot: (projectId: string) => Promise<LoadedSnapshot>;
}) {
  return renderWithSource(path, {
    projects: () => Promise.resolve(TEST_PROJECTS),
    snapshot,
    preloaded: {projects: TEST_PROJECTS},
  });
}

/** Like renderAppWithSource, with the same `load` for every project. */
export function renderAppWithLoader(load: () => Promise<LoadedSnapshot>) {
  return renderAppWithSource({snapshot: () => load()});
}
