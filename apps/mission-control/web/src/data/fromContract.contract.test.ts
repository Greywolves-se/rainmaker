// Maps the shared contract fixture. Excluded from `tsc` like the other *.contract.test files:
// the Docker build copies only web/, so the fixture import would not resolve there.
import {describe, expect, it} from 'vitest';
import sample from '../../../contract/fixtures/snapshot.sample.json';
import {parseSnapshot} from '../contract/snapshot';
import {fromContract} from './fromContract';

const page = fromContract(parseSnapshot(sample));

describe('fromContract', () => {
  it('maps the shell: time, project and source summary', () => {
    expect(page.generatedAt).toBe('2026-11-02T06:00:00Z');
    expect(page.projects).toEqual([
      {id: 'project', name: 'Example project', health: null},
    ]);
    expect(page.sourceSummary).toEqual({
      synced: 1,
      total: 2,
      note: 'risks.yaml: line 14 has no owner',
    });
  });

  it('groups current-sprint work items by status', () => {
    expect(page.overview.done).toEqual([]);
    expect(page.overview.inProgress).toEqual([
      {
        id: '101',
        title: 'Load the snapshot in the web app',
        meta: 'Alex Berg',
        url: 'https://github.com/example-org/example-repo/issues/101',
      },
    ]);
    expect(page.overview.sprint).toEqual({
      name: 'Sprint 4',
      done: 0,
      total: 2,
      url: 'https://github.com/example-org/example-repo/milestone/4',
    });
    expect(page.overview.blocked.map(item => item.id)).toEqual(['102']);
    expect(page.development.stories).toEqual([
      {
        id: '#101',
        title: 'Load the snapshot in the web app',
        owner: 'Alex Berg',
        status: 'inProgress',
      },
      {
        id: '#102',
        title: 'GitHub source for issues',
        owner: '',
        status: 'blocked',
      },
    ]);
    expect(page.development.sprint).toEqual({
      name: 'Sprint 4',
      dates: '2026-10-26 – 2026-11-06',
    });
  });

  it('lists only decisions that need an owner on the overview', () => {
    expect(page.overview.decisionMakers).toEqual(['Kim Ek']);
    expect(page.overview.decisions).toEqual([
      {
        id: 'B-13',
        title: 'Pilot price model',
        meta: 'Kim Ek · 2026-10-28',
        initial: 'K',
      },
    ]);
    expect(page.development.techDecisions.map(d => [d.id, d.status])).toEqual([
      ['B-12', 'decided'],
      ['B-13', 'proposed'],
    ]);
  });

  it('shows the most serious risk and open questions only', () => {
    expect(page.overview.risk).toEqual({
      title: 'Pilot publisher not signed in time',
      text: 'Weekly follow-up with two backup publishers',
    });
    expect(page.development.questions).toEqual([
      {
        id: 'Q-7',
        title: 'Which reader consent text do pilot publishers use?',
        meta: '2026-10-30',
        audience: 'Kim Ek',
        status: 'blocker',
      },
    ]);
  });

  it('maps pull requests, links, events and sources', () => {
    expect(
      page.development.pullRequests.map(pr => [pr.number, pr.status])
    ).toEqual([
      ['#110', 'review'],
      ['#108', 'merged'],
    ]);
    expect(page.development.links[0]).toEqual({
      id: 'staging',
      title: 'Staging',
      meta: 'staging.example.org',
    });
    expect(page.overview.events[0]).toEqual({
      id: 'ev-3',
      title: 'CI failed on develop',
      source: 'GH',
      sourceName: 'GitHub',
      occurredAt: '2026-11-01T16:09:30Z',
      url: 'https://github.com/example-org/example-repo/actions/runs/9001',
    });
    expect(page.overview.events.map(event => event.id)).toEqual([
      'ev-3',
      'ev-2',
      'ev-1',
    ]);
    expect(page.overview.release).toEqual({
      version: '0.3.0',
      environment: 'dev',
      publishedAt: '2026-10-31T12:00:00Z',
      url: 'https://github.com/example-org/example-repo/releases/tag/v0.3.0',
    });
    expect(page.overview.milestones.map(m => m.date)).toEqual([
      '2026-12-18',
      '2027-01-07',
    ]);
    expect(
      page.sources.cards.map(card => [card.code, card.status, card.lastSync])
    ).toEqual([
      ['GH', 'synced', null],
      ['DA', 'error', 'risks.yaml: line 14 has no owner'],
    ]);
  });

  it('leaves sections without contract data empty instead of inventing them', () => {
    expect(page.overview.pilotList).toEqual([]);
    expect(page.overview.awsCost).toBeNull();
    expect(page.development.build).toBeNull();
    expect(page.sources.mapping).toEqual([]);
    expect(page.sources.syncLog).toEqual([]);
    expect(page.navigator).toEqual({
      summaryTime: null,
      summary: null,
      projects: [],
      nextSteps: [],
      questions: [],
    });
  });
});
