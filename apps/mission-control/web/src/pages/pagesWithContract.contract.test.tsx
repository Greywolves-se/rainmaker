// Renders every page with the mapped contract fixture. Excluded from `tsc` like the other
// *.contract.test files: the Docker build copies only web/.
import {screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe, expect, it, vi} from 'vitest';
import sample from '../../../contract/fixtures/snapshot.sample.json';
import {parseSnapshot} from '../contract/snapshot';
import {fromContract} from '../data/fromContract';
import {renderApp} from '../test/renderApp';

vi.mock('../data/useSnapshot', () => ({
  useSnapshot: () => fromContract(parseSnapshot(sample)),
}));

describe('pages with contract data', () => {
  it('render real items, empty states and no sample data', async () => {
    renderApp();
    const menu = screen.getByRole('navigation', {name: 'Main menu'});

    expect(screen.getByText('Load the snapshot in the web app')).toBeVisible();
    expect(screen.getAllByText('No data yet').length).toBeGreaterThan(0);
    expect(screen.queryByText(/Retail customer/)).not.toBeInTheDocument();

    await userEvent.click(
      within(menu).getByRole('link', {name: 'Development'})
    );
    expect(screen.getByText('feat(#101): load the snapshot')).toBeVisible();

    await userEvent.click(within(menu).getByRole('link', {name: 'Sources'}));
    expect(
      screen.getByText('risks.yaml: line 14 has no owner', {
        selector: '.item__meta',
      })
    ).toBeVisible();
  });
});
