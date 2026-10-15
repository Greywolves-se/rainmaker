import {parseSnapshot, type Snapshot} from '../contract/snapshot';
import {fromContract} from './fromContract';
import {SAMPLE_SNAPSHOT} from './sampleSnapshot';
import type {Snapshot as PageSnapshot} from './types';

/** Where the snapshot is read from. Set VITE_SNAPSHOT_URL at build time to change it. */
export const SNAPSHOT_URL: string =
  import.meta.env.VITE_SNAPSHOT_URL || '/snapshot.json';

/** snapshot.json does not exist (404) or the server cannot be reached. */
export class SnapshotMissingError extends Error {}

/** What the app shows, plus what the shell needs to say how trustworthy it is. */
export interface LoadedSnapshot {
  page: PageSnapshot;
  /** True when the fictional sample is shown instead of a real snapshot. */
  demo: boolean;
  generatedAt: string;
  failedSources: {id: string; name: string; message: string | null}[];
}

export const DEMO_SNAPSHOT: LoadedSnapshot = {
  page: SAMPLE_SNAPSHOT,
  demo: true,
  generatedAt: SAMPLE_SNAPSHOT.generatedAt,
  failedSources: [],
};

/** Fetches and validates the snapshot. Throws SnapshotMissingError, an HTTP error or a ZodError. */
export async function fetchSnapshot(
  url: string,
  fetchImpl: typeof fetch = fetch
): Promise<Snapshot> {
  let response: Response;
  try {
    response = await fetchImpl(url, {cache: 'no-store'});
  } catch (error) {
    throw new SnapshotMissingError(`Could not reach ${url}`, {cause: error});
  }
  if (response.status === 404) {
    throw new SnapshotMissingError(`${url} was not found`);
  }
  if (!response.ok) {
    throw new Error(`${url} returned HTTP ${response.status}`);
  }
  return parseSnapshot(await response.json());
}

/**
 * Loads the snapshot for the app. With demoFallback (local dev only) a missing file gives the
 * fictional sample; an invalid file always fails, so a broken snapshot is never hidden.
 */
export async function loadSnapshot({
  url,
  demoFallback,
  fetchImpl = fetch,
}: {
  url: string;
  demoFallback: boolean;
  fetchImpl?: typeof fetch;
}): Promise<LoadedSnapshot> {
  try {
    const snapshot = await fetchSnapshot(url, fetchImpl);
    return {
      page: fromContract(snapshot),
      demo: false,
      generatedAt: snapshot.generatedAt,
      failedSources: snapshot.sources
        .filter(source => source.status === 'error')
        .map(({id, name, message}) => ({id, name, message})),
    };
  } catch (error) {
    if (demoFallback && error instanceof SnapshotMissingError) {
      return DEMO_SNAPSHOT;
    }
    throw error;
  }
}
