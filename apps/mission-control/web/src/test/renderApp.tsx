import {render} from '@testing-library/react';
import type {ReactNode} from 'react';
import {I18nextProvider} from 'react-i18next';
import {MemoryRouter} from 'react-router';
import {App} from '../App';
import {SnapshotDataProvider, SnapshotProvider} from '../data/SnapshotProvider';
import {DEMO_SNAPSHOT, type LoadedSnapshot} from '../data/snapshotClient';
import {createI18n} from '../i18n/i18n';

function renderWithProviders(path: string, snapshot: ReactNode) {
  return render(
    <I18nextProvider i18n={createI18n('en')}>
      <MemoryRouter initialEntries={[path]}>{snapshot}</MemoryRouter>
    </I18nextProvider>
  );
}

/**
 * Renders the whole app at `path` with its own i18n instance, so tests never share state.
 * The snapshot is already loaded: the demo sample unless `loaded` is given.
 */
export function renderApp({
  path = '/',
  loaded = DEMO_SNAPSHOT,
}: {path?: string; loaded?: LoadedSnapshot} = {}) {
  return renderWithProviders(
    path,
    <SnapshotDataProvider loaded={loaded}>
      <App />
    </SnapshotDataProvider>
  );
}

/** Renders the whole app the way main.tsx does, with `load` standing in for the fetch. */
export function renderAppWithLoader(load: () => Promise<LoadedSnapshot>) {
  return renderWithProviders(
    '/',
    <SnapshotProvider load={load}>
      <App />
    </SnapshotProvider>
  );
}
