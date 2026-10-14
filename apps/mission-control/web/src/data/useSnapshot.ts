import {SAMPLE_SNAPSHOT} from './sampleSnapshot';
import type {Snapshot} from './types';

/** The single place components get their data from, so the source can be swapped in one spot. */
export function useSnapshot(): Snapshot {
  // TODO(#10): load the collector's snapshot instead of the fictional sample.
  return SAMPLE_SNAPSHOT;
}
