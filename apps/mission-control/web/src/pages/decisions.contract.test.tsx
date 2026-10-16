// Decisions on the Overview and Development pages, from the shared contract sample. Excluded
// from `tsc` like the other *.contract.test files: the Docker build copies only web/.
import {screen, within} from '@testing-library/react';
import {describe, expect, it} from 'vitest';
import sample from '../../../contract/fixtures/snapshot.sample.json';
import {parseSnapshot} from '../contract/snapshot';
import {fromContract} from '../data/fromContract';
import type {LoadedSnapshot} from '../data/snapshotClient';
import {renderApp} from '../test/renderApp';

function loaded(decisions: unknown[] = sample.decisions): LoadedSnapshot {
  const snapshot = parseSnapshot({...sample, decisions});
  return {
    page: fromContract(snapshot),
    demo: false,
    generatedAt: snapshot.generatedAt,
    failedSources: [],
  };
}

const card = (heading: string | RegExp) =>
  screen.getByRole('heading', {name: heading}).closest('section')!;

describe('decisions', () => {
  it('shows the waiting decision on the Overview, linked to its YAML line', () => {
    renderApp({loaded: loaded()});

    const waiting = card('Decisions needing Kim Ek');
    expect(
      within(waiting).getByRole('link', {name: 'Pilot price model'})
    ).toHaveAttribute(
      'href',
      'https://github.com/example-org/example-repo/blob/develop/data/decisions.yaml#L9'
    );
    expect(waiting).not.toHaveTextContent('one snapshot file');
  });

  it('shows the other decisions on Development with id, owner, date, reference and status', () => {
    renderApp({path: '/development', loaded: loaded()});

    const technical = card('Technical decisions and dependencies');
    expect(technical).toHaveTextContent('B-12');
    expect(technical).toHaveTextContent('Kim Ek · 15 Oct · Architecture 10.6');
    expect(within(technical).getByText('Decided')).toBeVisible();
    expect(
      within(technical).getByRole('link', {
        name: 'Use one snapshot file instead of a backend in V1',
      })
    ).toHaveAttribute(
      'href',
      'https://github.com/example-org/example-repo/blob/develop/data/decisions.yaml#L1'
    );
    expect(technical).not.toHaveTextContent('Pilot price model');
  });

  it('says so on the Overview when nothing is waiting', () => {
    renderApp({loaded: loaded([])});
    expect(card('Decisions waiting for an owner')).toHaveTextContent(
      'No decisions waiting'
    );
  });

  it('says so when there are no other decisions', () => {
    renderApp({path: '/development', loaded: loaded([])});
    expect(card('Technical decisions and dependencies')).toHaveTextContent(
      'No decisions yet'
    );
  });
});
