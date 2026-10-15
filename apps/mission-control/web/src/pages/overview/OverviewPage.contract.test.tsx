// Renders the Overview page from the shared contract sample. Excluded from `tsc` like the other
// *.contract.test files: the Docker build copies only web/.
import {screen, within} from '@testing-library/react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import sample from '../../../../contract/fixtures/snapshot.sample.json';
import {parseSnapshot} from '../../contract/snapshot';
import {fromContract} from '../../data/fromContract';
import type {LoadedSnapshot} from '../../data/snapshotClient';
import {renderApp} from '../../test/renderApp';

/** The sample with some fields replaced; parseSnapshot checks the result is still valid. */
function loaded(changes: Record<string, unknown> = {}): LoadedSnapshot {
  const snapshot = parseSnapshot({...sample, ...changes});
  return {
    page: fromContract(snapshot),
    demo: false,
    generatedAt: snapshot.generatedAt,
    failedSources: [],
  };
}

const card = (heading: string) =>
  screen.getByRole('heading', {name: heading}).closest('section')!;

describe('Overview page with the sample snapshot', () => {
  beforeEach(() => {
    vi.useFakeTimers({toFake: ['Date']});
    vi.setSystemTime(new Date('2026-11-02T06:05:00Z'));
  });
  afterEach(() => vi.useRealTimers());

  it('shows the KPI cards with links to their details', () => {
    renderApp({loaded: loaded()});

    const sprint = card('Sprint progress');
    expect(sprint).toHaveTextContent('0 of 2 done');
    expect(within(sprint).getByRole('link')).toHaveAttribute(
      'href',
      'https://github.com/example-org/example-repo/milestone/4'
    );

    const blocked = card('Blocked items');
    expect(blocked).toHaveTextContent('1');
    expect(within(blocked).getByRole('link')).toHaveAttribute(
      'href',
      '/development'
    );

    const release = card('Latest release');
    expect(release).toHaveTextContent('0.3.0');
    expect(within(release).getByRole('link')).toHaveAttribute(
      'href',
      'https://github.com/example-org/example-repo/releases/tag/v0.3.0'
    );

    expect(card('Technically ready in UAT')).toHaveTextContent('46 days left');
    expect(card('External pilot starts')).toHaveTextContent('66 days left');
  });

  it('links status items and lists events newest first with time and link', () => {
    renderApp({loaded: loaded()});

    expect(
      screen.getByRole('link', {name: 'Load the snapshot in the web app'})
    ).toHaveAttribute(
      'href',
      'https://github.com/example-org/example-repo/issues/101'
    );

    const events = within(card('Recent events')).getAllByRole('link');
    expect(events.map(link => link.textContent)).toEqual([
      'CI failed on develop',
      'PR #108 merged',
      'Change C-2: load balancer added',
    ]);
    expect(card('Recent events')).toHaveTextContent('GitHub · 14 hours ago');
  });

  it('says the cost is not connected and that there are no pilots', () => {
    renderApp({loaded: loaded()});

    const risks = card('Risks, pilots and cost');
    expect(risks).toHaveTextContent('No pilots yet');
    expect(risks).toHaveTextContent('Not connected yet');
  });

  it('has empty states that read well', () => {
    renderApp({
      loaded: loaded({sprint: null, workItems: [], releases: [], events: []}),
    });

    expect(card('Sprint progress')).toHaveTextContent('No active sprint');
    expect(card('Blocked items')).toHaveTextContent('0');
    expect(card('Latest release')).toHaveTextContent('No release yet');
    expect(card('Current status')).toHaveTextContent('No blockers');
    expect(card('Current status')).toHaveTextContent('Nothing in progress');
    expect(card('Recent events')).toHaveTextContent('No events yet');
  });

  it('never turns a non-web URL into a link', () => {
    const events = [{...sample.events[0], url: 'javascript:alert(1)'}];

    renderApp({loaded: loaded({events})});

    expect(
      within(card('Recent events')).queryByRole('link')
    ).not.toBeInTheDocument();
    expect(card('Recent events')).toHaveTextContent('CI failed on develop');
  });
});
