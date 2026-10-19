// "Latest documents" (#114) with the shared contract sample. Excluded from `tsc` like the other
// *.contract.test files: the Docker build copies only web/.
import {screen, within} from '@testing-library/react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import sample from '../../../../contract/fixtures/snapshot.sample.json';
import {parseSnapshot} from '../../contract/snapshot';
import {toLoadedSnapshot} from '../../data/snapshotClient';
import {renderApp} from '../../test/renderApp';

/** The sample with some fields replaced, loaded the way the app loads a real snapshot. */
const loaded = (changes: Record<string, unknown> = {}) =>
  toLoadedSnapshot(parseSnapshot({...sample, ...changes}));

const card = (heading: string) =>
  screen.getByRole('heading', {name: heading}).closest('section')!;

const drive = sample.sources.find(source => source.id === 'google-drive')!;
const otherSources = sample.sources.filter(
  source => source.id !== 'google-drive'
);

const documentAt = (n: number, modifiedAt: string) => ({
  ...sample.documents[0],
  id: `gd-${n}`,
  title: `Document ${n}`,
  modifiedAt,
});

describe('Latest documents', () => {
  beforeEach(() => {
    vi.useFakeTimers({toFake: ['Date']});
    vi.setSystemTime(new Date('2026-11-02T06:05:00Z'));
  });
  afterEach(() => vi.useRealTimers());

  it('lists the documents newest first, each opening Drive in a new tab', () => {
    renderApp({loaded: loaded()});

    const links = within(card('Latest documents')).getAllByRole('link');
    expect(links.map(link => link.textContent)).toEqual([
      'Pilot agreement draft',
      'Sprint 3 test report',
    ]);
    expect(links[0]).toHaveAttribute(
      'href',
      'https://docs.google.com/document/d/1a2b3c/edit'
    );
    expect(links[0]).toHaveAttribute('target', '_blank');
    expect(links[0]).toHaveAttribute('rel', 'noreferrer');
  });

  it('shows the kind, the last editor and how long ago', () => {
    renderApp({loaded: loaded()});

    const documents = card('Latest documents');
    expect(documents).toHaveTextContent('Document · Kim Ek · 16 hours ago');
    // No last editor: the line just leaves it out.
    expect(documents).toHaveTextContent('Spreadsheet · 3 days ago');
  });

  it('shows at most the 8 newest documents', () => {
    const documents = Array.from({length: 12}, (_, n) =>
      documentAt(n, `2026-10-${String(10 + n).padStart(2, '0')}T12:00:00Z`)
    );

    renderApp({loaded: loaded({documents})});

    const titles = within(card('Latest documents'))
      .getAllByRole('link')
      .map(link => link.textContent);
    expect(titles).toHaveLength(8);
    expect(titles[0]).toBe('Document 11');
    expect(titles[7]).toBe('Document 4');
  });

  it('says so when Drive works but has no documents', () => {
    renderApp({loaded: loaded({documents: []})});

    expect(card('Latest documents')).toHaveTextContent('No documents yet');
  });

  it('shows the red source banner and "No data yet" when Drive failed', () => {
    const failed = {
      ...drive,
      status: 'error',
      message: 'Google Drive returned 403',
    };

    renderApp({loaded: loaded({sources: [...otherSources, failed]})});

    expect(
      screen.getAllByRole('alert').map(alert => alert.textContent)
    ).toContain('Google Drive could not be updated: Google Drive returned 403');
    expect(card('Latest documents')).toHaveTextContent('No data yet');
    expect(
      within(card('Latest documents')).queryByRole('link')
    ).not.toBeInTheDocument();
  });

  it('shows "No data yet" for a snapshot without a Drive source', () => {
    renderApp({loaded: loaded({sources: otherSources, documents: []})});

    expect(card('Latest documents')).toHaveTextContent('No data yet');
  });

  it('shows Drive events in Recent events', () => {
    const events = [
      {
        id: 'ev-doc',
        occurredAt: '2026-11-01T14:20:00Z',
        title: 'Document updated: Pilot agreement draft',
        source: 'google-drive',
        url: 'https://docs.google.com/document/d/1a2b3c/edit',
      },
    ];

    renderApp({loaded: loaded({events})});

    const recent = card('Recent events');
    expect(
      within(recent).getByRole('link', {
        name: 'Document updated: Pilot agreement draft',
      })
    ).toBeVisible();
    expect(recent).toHaveTextContent('Google Drive · 16 hours ago');
  });

  it('lists Google Drive with its status on the Sources page', () => {
    renderApp({path: '/rainmaker/sources', loaded: loaded()});

    const driveCard = screen
      .getByRole('heading', {name: 'Google Drive'})
      .closest('section')!;
    expect(within(driveCard).getByText('Synced')).toBeVisible();
    expect(driveCard.querySelector('.tile')).toHaveTextContent('GD');
  });
});
