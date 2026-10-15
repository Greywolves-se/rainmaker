import {screen} from '@testing-library/react';
import {describe, expect, it} from 'vitest';
import {renderApp} from '../../test/renderApp';

describe('Overview page with the demo sample', () => {
  it('renders every card', () => {
    renderApp();

    for (const heading of [
      'Sprint progress',
      'Blocked items',
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
});
