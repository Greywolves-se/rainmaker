import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type {ProjectList} from '../contract/projects';
import {loadProjects} from './projectsClient';
import {loadSnapshot, snapshotUrl, type LoadedSnapshot} from './snapshotClient';

/** Where the app gets its data. main.tsx uses the files; tests hand in their own. */
export interface DataSource {
  projects(): Promise<ProjectList>;
  snapshot(projectId: string): Promise<LoadedSnapshot>;
  /** Data that is already there (tests): shown at once, without a loading step. */
  preloaded?: {
    projects?: ProjectList;
    snapshot?: (projectId: string) => LoadedSnapshot;
  };
}

/** The collector's files. Demo data only in local dev, when a file does not exist yet. */
export function fileDataSource(demoFallback: boolean): DataSource {
  return {
    projects: () => loadProjects({demoFallback}),
    snapshot: projectId =>
      loadSnapshot({url: snapshotUrl(projectId), demoFallback}),
  };
}

const DataSourceContext = createContext<DataSource | null>(null);

export function DataSourceProvider({
  source,
  children,
}: {
  source: DataSource;
  children: ReactNode;
}) {
  return (
    <DataSourceContext.Provider value={source}>
      {children}
    </DataSourceContext.Provider>
  );
}

export function useDataSource(): DataSource {
  const source = useContext(DataSourceContext);
  if (!source) {
    throw new Error('useDataSource() must be used inside a DataSourceProvider');
  }
  return source;
}

export type LoadState<T> =
  | {status: 'loading'}
  | {status: 'error'; message: string}
  | {status: 'ready'; value: T};

/**
 * Runs `load` once and tracks its result. Give the component a `key` per thing it loads (e.g.
 * the project id), so a new thing starts from "loading" instead of showing the old one.
 */
export function useLoad<T>(
  load: () => Promise<T>,
  preloaded: T | undefined,
  what: string
): LoadState<T> {
  const [state, setState] = useState<LoadState<T>>(
    preloaded === undefined
      ? {status: 'loading'}
      : {status: 'ready', value: preloaded}
  );
  const needsLoad = preloaded === undefined;
  // Loads once per mount; the caller's `key` decides when a new load starts.
  const loadOnce = useRef(load);

  useEffect(() => {
    if (!needsLoad) {
      return;
    }
    let cancelled = false;
    loadOnce.current().then(
      value => {
        if (!cancelled) {
          setState({status: 'ready', value});
        }
      },
      (error: unknown) => {
        console.error(`Mission Control: could not load ${what}`, error);
        if (!cancelled) {
          setState({status: 'error', message: String(error)});
        }
      }
    );
    return () => {
      cancelled = true;
    };
  }, [needsLoad, what]);

  return state;
}
