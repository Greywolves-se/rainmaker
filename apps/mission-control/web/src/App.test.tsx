import {screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe, expect, it} from 'vitest';
import {renderApp} from './test/renderApp';

describe('App', () => {
  it('starts on the overview page', () => {
    renderApp();

    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Overview'
    );
    expect(document.title).toBe('Overview – Mission Control');
  });

  it('navigates between pages and updates the page title', async () => {
    renderApp();
    const menu = screen.getByRole('navigation', {name: 'Main menu'});

    await userEvent.click(
      within(menu).getByRole('link', {name: 'Development'})
    );
    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Development'
    );
    expect(document.title).toBe('Development – Mission Control');

    await userEvent.click(within(menu).getByRole('link', {name: 'Sources'}));
    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Sources'
    );
    expect(within(menu).getByRole('link', {name: 'Sources'})).toHaveAttribute(
      'aria-current',
      'page'
    );
  });

  it('shows the sample-data banner on every page', async () => {
    renderApp();
    const menu = screen.getByRole('navigation', {name: 'Main menu'});

    for (const page of ['Overview', 'Development', 'Sources']) {
      await userEvent.click(within(menu).getByRole('link', {name: page}));
      expect(screen.getByRole('note')).toHaveTextContent(
        'Sample data – not real project status'
      );
    }
  });

  it('sends unknown paths to the overview', () => {
    renderApp({path: '/does-not-exist'});

    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Overview'
    );
  });

  it('shows the answer to the question picked in Navigator', async () => {
    renderApp();

    await userEvent.click(screen.getByRole('button', {name: 'Any blockers?'}));

    expect(
      screen.getByText(/AWS health is missing an IAM role \(Erik\)/)
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Any blockers?'})).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });
});
