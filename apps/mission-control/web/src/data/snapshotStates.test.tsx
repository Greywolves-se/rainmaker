import {screen} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {renderApp, renderAppWithLoader} from '../test/renderApp';
import {SAMPLE_SNAPSHOT} from './sampleSnapshot';
import type {LoadedSnapshot} from './snapshotClient';

const live = (overrides: Partial<LoadedSnapshot> = {}): LoadedSnapshot => ({
  page: SAMPLE_SNAPSHOT,
  demo: false,
  generatedAt: '2026-11-02T06:00:00Z',
  failedSources: [],
  ...overrides,
});

const at = (isoTime: string) => {
  vi.useFakeTimers({toFake: ['Date']});
  vi.setSystemTime(new Date(isoTime));
};

describe('snapshot states', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('shows a loading message until the snapshot arrives', () => {
    renderAppWithLoader(() => new Promise(() => {}));

    expect(screen.getByRole('status')).toHaveTextContent(
      'Loading the snapshot …'
    );
    // The menu stays, so you can switch project while one is loading.
    expect(screen.getByRole('navigation', {name: 'Main menu'})).toBeVisible();
    expect(screen.queryByRole('heading', {level: 1})).not.toBeInTheDocument();
  });

  it('shows an error with the reason when loading fails', async () => {
    const logged = vi.spyOn(console, 'error').mockImplementation(() => {});

    renderAppWithLoader(() => Promise.reject(new Error('HTTP 500')));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Could not load the snapshot');
    expect(alert).toHaveTextContent('HTTP 500');
    expect(logged).toHaveBeenCalled();
  });

  it('shows the app once the snapshot has loaded', async () => {
    renderAppWithLoader(() => Promise.resolve(live()));

    expect(
      await screen.findByRole('heading', {level: 1, name: 'Overview'})
    ).toBeInTheDocument();
  });

  it('shows how old fresh data is, without a warning', () => {
    at('2026-11-02T06:05:00Z');

    renderApp({loaded: live()});

    expect(screen.getByText('Updated 5 minutes ago')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('warns when the data is older than 24 hours', () => {
    at('2026-11-03T07:00:00Z');

    renderApp({loaded: live()});

    expect(screen.getByText('Updated 25 hours ago')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(
      'The data is more than 24 hours old'
    );
  });

  it('shows the demo banner only for demo data', () => {
    renderApp({loaded: live()});

    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });

  it('shows a banner for every source that failed', () => {
    at('2026-11-02T06:05:00Z');

    renderApp({
      loaded: live({
        failedSources: [
          {
            id: 'data-files',
            name: 'Data files',
            message: 'risks.yaml: line 14 has no owner',
          },
          {id: 'slack', name: 'Slack', message: null},
        ],
      }),
    });

    const alerts = screen.getAllByRole('alert').map(a => a.textContent);
    expect(alerts).toEqual([
      'Data files could not be updated: risks.yaml: line 14 has no owner',
      'Slack could not be updated.',
    ]);
  });
});
