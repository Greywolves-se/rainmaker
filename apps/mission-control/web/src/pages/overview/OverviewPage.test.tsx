import {screen} from '@testing-library/react';
import {describe, expect, it} from 'vitest';
import {renderApp} from '../../test/renderApp';

describe('Overview page with the demo sample', () => {
  it('renders every card', () => {
    renderApp();

    for (const heading of [
      'Sprint progress',
      'Blocked in this sprint',
      'Latest release',
      'Technically ready in UAT',
      'External pilot starts',
      'Current status',
      'Risks, pilots and cost',
      'Latest working version',
      'Recent events',
    ]) {
      expect(screen.getByRole('heading', {name: heading})).toBeVisible();
    }
    expect(screen.getByText('2 of 7 done')).toBeVisible();
    expect(screen.getByText('New pilot activated')).toBeVisible();
  });

  it('shows "Release notes" as a disabled button when there is no link', () => {
    renderApp();

    expect(screen.getByRole('button', {name: 'Release notes'})).toBeDisabled();
    expect(
      screen.queryByRole('link', {name: 'Release notes'})
    ).not.toBeInTheDocument();
  });
});
