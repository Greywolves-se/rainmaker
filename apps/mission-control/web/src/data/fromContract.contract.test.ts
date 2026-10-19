// Maps the shared contract fixture. Excluded from `tsc` like the other *.contract.test files:
// the Docker build copies only web/, so the fixture import would not resolve there.
import {describe, expect, it} from 'vitest';
import sample from '../../../contract/fixtures/snapshot.sample.json';
import {parseSnapshot} from '../contract/snapshot';
import {fromContract} from './fromContract';

const page = fromContract(parseSnapshot(sample));

describe('fromContract', () => {
  it('maps the shell: time and source summary', () => {
    expect(page.generatedAt).toBe('2026-11-02T06:00:00Z');
    expect(page.sourceSummary).toEqual({
      synced: 2,
      total: 3,
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
    expect(page.development.stories[0]).toEqual({
      id: '101',
      title: 'Load the snapshot in the web app',
      assignees: ['Alex Berg'],
      labels: ['area: web'],
      status: 'inProgress',
      inCurrentSprint: true,
      createdAt: '2026-10-20T09:12:00Z',
      url: 'https://github.com/example-org/example-repo/issues/101',
    });
    // All work items, not only the sprint: the page filters them.
    expect(
      page.development.stories.map(s => [s.id, s.status, s.inCurrentSprint])
    ).toEqual([
      ['101', 'inProgress', true],
      ['102', 'blocked', true],
      ['97', 'done', false],
    ]);
    expect(page.development.sprint).toEqual({
      name: 'Sprint 4',
      startDate: '2026-10-26',
      endDate: '2026-11-06',
      url: 'https://github.com/example-org/example-repo/milestone/4',
    });
  });

  it('puts decisions waiting for an owner on the overview, the rest on development', () => {
    expect(page.overview.decisionMakers).toEqual(['Kim Ek']);
    expect(page.overview.decisions).toEqual([
      {
        id: 'B-13',
        title: 'Pilot price model',
        owner: 'Kim Ek',
        date: '2026-10-28',
        status: 'proposed',
        reference: null,
        url: 'https://github.com/example-org/example-repo/blob/develop/data/decisions.yaml#L9',
      },
    ]);
    expect(page.development.techDecisions.map(d => [d.id, d.status])).toEqual([
      ['B-12', 'decided'],
    ]);
  });

  it('only counts a decision as waiting while it is still proposed', () => {
    const decision = sample.decisions[1]!;
    const decisions = [
      {...decision, id: 'newer', date: '2026-10-28'},
      {...decision, id: 'older', date: '2026-10-02'},
      {...decision, id: 'already-decided', status: 'decided'},
      {...decision, id: 'replaced', status: 'superseded'},
      {...decision, id: 'technical', needsOwnerDecision: false},
    ];

    const mapped = fromContract(parseSnapshot({...sample, decisions}));

    expect(mapped.overview.decisions.map(d => d.id)).toEqual([
      'older',
      'newer',
    ]);
    expect(mapped.development.techDecisions.map(d => d.id)).toEqual([
      'already-decided',
      'replaced',
      'technical',
    ]);
  });

  it('shows every risk red first, with owner and mitigation, and open questions only', () => {
    expect(page.overview.risks).toEqual([
      {
        id: 'R-3',
        title: 'Pilot publisher not signed in time',
        level: 'red',
        owner: 'Kim Ek',
        mitigation: 'Weekly follow-up with two backup publishers',
      },
      {
        id: 'R-5',
        title: 'Test coverage below target',
        level: 'yellow',
        owner: 'Sam Lind',
        mitigation: 'Coverage gate on domain packages',
      },
    ]);
    expect(page.development.questions).toEqual([
      {
        id: 'Q-7',
        title: 'Which reader consent text do pilot publishers use?',
        meta: '',
        audience: 'Kim Ek',
        neededBy: '2026-10-30',
        status: 'blocker',
      },
    ]);
  });

  it('maps the latest documents newest first from a working source', () => {
    expect(page.overview.documents).toEqual({
      status: 'ok',
      items: [
        {
          id: 'gd-1a2b3c',
          title: 'Pilot agreement draft',
          kind: 'document',
          lastEditor: 'Kim Ek',
          modifiedAt: '2026-11-01T14:20:00Z',
          url: 'https://docs.google.com/document/d/1a2b3c/edit',
        },
        {
          id: 'gd-4d5e6f',
          title: 'Sprint 3 test report',
          kind: 'spreadsheet',
          lastEditor: null,
          modifiedAt: '2026-10-30T08:05:00Z',
          url: 'https://docs.google.com/spreadsheets/d/4d5e6f/edit',
        },
      ],
    });
  });

  describe('documents from more than one source', () => {
    const otherStore = {
      id: 'other-store',
      name: 'Other store',
      status: 'ok',
      fetchedAt: null,
      message: null,
    };
    const fromOther = {
      ...sample.documents[0],
      id: 'os-1',
      title: 'From the other store',
      source: 'other-store',
    };
    const withStatus = (driveStatus: string, otherStatus: string) =>
      fromContract(
        parseSnapshot({
          ...sample,
          sources: [
            ...sample.sources.map(source =>
              source.id === 'google-drive'
                ? {...source, status: driveStatus}
                : source
            ),
            {...otherStore, status: otherStatus},
          ],
          documents: [...sample.documents, fromOther],
        })
      ).overview.documents;
    const titles = (documents: ReturnType<typeof withStatus>) =>
      documents?.status === 'ok' ? documents.items.map(d => d.title) : [];

    it('keeps only documents whose own source works', () => {
      expect(titles(withStatus('ok', 'error'))).toEqual([
        'Pilot agreement draft',
        'Sprint 3 test report',
      ]);
      expect(titles(withStatus('error', 'ok'))).toEqual([
        'From the other store',
      ]);
    });

    it('is unavailable when no document source works', () => {
      expect(withStatus('error', 'error')).toEqual({status: 'unavailable'});
      expect(withStatus('error', 'disabled')).toEqual({
        status: 'unavailable',
      });
    });

    it('is off, with the names, when every document source is disabled', () => {
      expect(withStatus('disabled', 'disabled')).toEqual({
        status: 'off',
        sources: ['Google Drive', 'Other store'],
      });
    });
  });

  it('is unavailable for a snapshot without a document source', () => {
    const old = fromContract(
      parseSnapshot({
        ...sample,
        sources: sample.sources.filter(s => s.id !== 'google-drive'),
        documents: [],
      })
    );
    expect(old.overview.documents).toEqual({status: 'unavailable'});
  });

  it('sorts events by time, not by text', () => {
    // As text, "…30.5Z" sorts before "…30Z", although it is half a second later.
    const events = [
      {
        ...sample.events[0],
        id: 'whole-second',
        occurredAt: '2026-11-01T16:09:30Z',
      },
      {...sample.events[0], id: 'later', occurredAt: '2026-11-01T16:09:30.5Z'},
    ];

    const sorted = fromContract(parseSnapshot({...sample, events}));

    expect(sorted.overview.events.map(event => event.id)).toEqual([
      'later',
      'whole-second',
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
      ['GD', 'synced', null],
    ]);
  });

  it('leaves sections without contract data empty instead of inventing them', () => {
    expect(page.overview.pilotList).toEqual([]);
    expect(page.overview.awsCost).toBeNull();
    expect(page.development.build).toBeNull();
    expect(page.sources.mapping).toEqual([]);
    expect(page.sources.syncLog).toEqual([]);
    // The sample has AI text (#115) but no navigator-ai source entry: shown, no reason.
    expect(page.navigator).toMatchObject({status: 'on', reason: null});
  });
});
