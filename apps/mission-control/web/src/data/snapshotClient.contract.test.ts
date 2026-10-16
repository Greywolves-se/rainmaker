// Uses the shared contract fixture. Excluded from `tsc` like the other *.contract.test files:
// the Docker build copies only web/.
import {describe, expect, it, vi} from 'vitest';
import sample from '../../../contract/fixtures/snapshot.sample.json';
import {SAMPLE_SNAPSHOT} from './sampleSnapshot';
import {
  SnapshotMissingError,
  fetchSnapshot,
  loadSnapshot,
} from './snapshotClient';

const respond = (body: unknown, status = 200) =>
  vi.fn().mockResolvedValue(new Response(JSON.stringify(body), {status}));

describe('fetchSnapshot', () => {
  it('returns the parsed snapshot', async () => {
    const fetchImpl = respond(sample);

    const snapshot = await fetchSnapshot('/snapshot.json', fetchImpl);

    expect(snapshot.project.name).toBe('Example project');
    expect(fetchImpl).toHaveBeenCalledWith('/snapshot.json', {
      cache: 'no-store',
    });
  });

  it('reports a 404 or an unreachable server as missing', async () => {
    await expect(
      fetchSnapshot('/snapshot.json', respond({}, 404))
    ).rejects.toBeInstanceOf(SnapshotMissingError);
    await expect(
      fetchSnapshot(
        '/snapshot.json',
        vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
      )
    ).rejects.toBeInstanceOf(SnapshotMissingError);
  });

  it('reports other HTTP errors and invalid files as real errors', async () => {
    const serverError = fetchSnapshot('/snapshot.json', respond({}, 500));
    await expect(serverError).rejects.toThrow('HTTP 500');
    await expect(serverError).rejects.not.toBeInstanceOf(SnapshotMissingError);

    await expect(
      fetchSnapshot('/snapshot.json', respond({...sample, generatedAt: 'x'}))
    ).rejects.toThrow(/generatedAt/);
  });
});

describe('loadSnapshot', () => {
  it('maps a real snapshot and lists the sources that failed', async () => {
    const loaded = await loadSnapshot({
      url: '/snapshot.json',
      demoFallback: false,
      fetchImpl: respond(sample),
    });

    expect(loaded.demo).toBe(false);
    expect(loaded.generatedAt).toBe('2026-11-02T06:00:00Z');
    expect(loaded.page.overview.sprint?.name).toBe('Sprint 4');
    expect(loaded.failedSources).toEqual([
      {
        id: 'data-files',
        name: 'Data files',
        message: 'risks.yaml: line 14 has no owner',
      },
    ]);
  });

  it('falls back to demo data only when allowed and the file is missing', async () => {
    const demo = await loadSnapshot({
      url: '/snapshot.json',
      demoFallback: true,
      fetchImpl: respond({}, 404),
    });
    expect(demo.demo).toBe(true);
    expect(demo.page).toBe(SAMPLE_SNAPSHOT);

    await expect(
      loadSnapshot({
        url: '/snapshot.json',
        demoFallback: false,
        fetchImpl: respond({}, 404),
      })
    ).rejects.toBeInstanceOf(SnapshotMissingError);
  });

  it('never hides an invalid snapshot behind demo data', async () => {
    await expect(
      loadSnapshot({
        url: '/snapshot.json',
        demoFallback: true,
        fetchImpl: respond({schemaVersion: 2}),
      })
    ).rejects.toThrow();
  });
});
