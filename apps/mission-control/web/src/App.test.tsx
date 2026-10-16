import {screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, describe, expect, it} from 'vitest';
import {LANGUAGE_STORAGE_KEY} from './i18n/i18n';
import {renderApp} from './test/renderApp';

describe('App', () => {
  afterEach(() => {
    localStorage.clear();
  });

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

  it('treats an unknown path as an unknown project', () => {
    renderApp({path: '/does-not-exist'});

    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Project not found'
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

  it('switches language and remembers the choice', async () => {
    renderApp();

    await userEvent.click(screen.getByRole('button', {name: 'Svenska'}));

    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Översikt'
    );
    expect(document.title).toBe('Översikt – Mission Control');
    expect(
      screen.getByText('Senast uppdaterad: ons 14 okt. 2026 · 10:24')
    ).toBeInTheDocument();
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('sv');
  });
});
