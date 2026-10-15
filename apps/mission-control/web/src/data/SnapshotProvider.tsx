import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import {useTranslation} from 'react-i18next';
import type {LoadedSnapshot} from './snapshotClient';

const SnapshotContext = createContext<LoadedSnapshot | null>(null);

type LoadState =
  | {status: 'loading'}
  | {status: 'error'; message: string}
  | {status: 'ready'; loaded: LoadedSnapshot};

/** Loads the snapshot once and shows a loading or error page until it is ready. */
export function SnapshotProvider({
  load,
  children,
}: {
  load: () => Promise<LoadedSnapshot>;
  children: ReactNode;
}) {
  const {t} = useTranslation();
  const [state, setState] = useState<LoadState>({status: 'loading'});

  useEffect(() => {
    let cancelled = false;
    load().then(
      loaded => {
        if (!cancelled) {
          setState({status: 'ready', loaded});
        }
      },
      (error: unknown) => {
        console.error('Mission Control: could not load the snapshot', error);
        if (!cancelled) {
          setState({status: 'error', message: String(error)});
        }
      }
    );
    return () => {
      cancelled = true;
    };
  }, [load]);

  if (state.status === 'loading') {
    return (
      <p className="app-status" role="status">
        {t('snapshot.loading')}
      </p>
    );
  }
  if (state.status === 'error') {
    return (
      <div className="app-status app-status--error" role="alert">
        <h1>{t('snapshot.errorTitle')}</h1>
        <p>{t('snapshot.errorHelp')}</p>
        <details>
          <summary>{t('snapshot.errorDetails')}</summary>
          <pre>{state.message}</pre>
        </details>
      </div>
    );
  }
  return (
    <SnapshotDataProvider loaded={state.loaded}>
      {children}
    </SnapshotDataProvider>
  );
}

/** Provides an already loaded snapshot (used by SnapshotProvider and by tests). */
export function SnapshotDataProvider({
  loaded,
  children,
}: {
  loaded: LoadedSnapshot;
  children: ReactNode;
}) {
  return (
    <SnapshotContext.Provider value={loaded}>
      {children}
    </SnapshotContext.Provider>
  );
}

export function useLoadedSnapshot(): LoadedSnapshot {
  const loaded = useContext(SnapshotContext);
  if (!loaded) {
    throw new Error(
      'useLoadedSnapshot() must be used inside a SnapshotProvider'
    );
  }
  return loaded;
}
