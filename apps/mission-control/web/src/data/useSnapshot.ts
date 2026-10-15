import {useLoadedSnapshot} from './SnapshotProvider';
import type {Snapshot} from './types';

/** The single place components get their page data from (real snapshot or demo sample). */
export function useSnapshot(): Snapshot {
  return useLoadedSnapshot().page;
}
