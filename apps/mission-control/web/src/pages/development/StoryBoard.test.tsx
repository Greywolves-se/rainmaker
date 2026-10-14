import {screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe, expect, it} from 'vitest';
import {renderApp} from '../../test/renderApp';

function storyRow(id: string): HTMLElement {
  const row = screen.getByRole('cell', {name: id}).closest('tr');
  if (!row) {
    throw new Error(`No table row for ${id}`);
  }
  return row;
}

describe('StoryBoard', () => {
  it('adds a story with the next id and updates the summary', async () => {
    renderApp({path: '/development'});
    expect(
      screen.getByText('7 stories · 2 done · 1 blocked')
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: '+ Add story'}));
    await userEvent.type(
      screen.getByLabelText('User story'),
      '  Show the sync log  '
    );
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'done');
    await userEvent.click(screen.getByRole('button', {name: 'Add'}));

    const row = storyRow('MC-08');
    expect(within(row).getByText('Show the sync log')).toBeInTheDocument();
    expect(within(row).getByText('Unassigned')).toBeInTheDocument();
    expect(
      screen.getByText('8 stories · 3 done · 1 blocked')
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('User story')).not.toBeInTheDocument();
  });

  it('does not add a story without a title', async () => {
    renderApp({path: '/development'});

    await userEvent.click(screen.getByRole('button', {name: '+ Add story'}));
    await userEvent.type(screen.getByLabelText('User story'), '   ');
    await userEvent.click(screen.getByRole('button', {name: 'Add'}));

    expect(screen.queryByRole('cell', {name: 'MC-08'})).not.toBeInTheDocument();
    expect(
      screen.getByText('7 stories · 2 done · 1 blocked')
    ).toBeInTheDocument();
  });

  it('changes the status of a story', async () => {
    renderApp({path: '/development'});

    await userEvent.selectOptions(
      screen.getByRole('combobox', {name: 'Status of MC-06'}),
      'done'
    );

    expect(
      screen.getByText('7 stories · 3 done · 0 blocked')
    ).toBeInTheDocument();
  });

  it('removes a story', async () => {
    renderApp({path: '/development'});

    await userEvent.click(screen.getByRole('button', {name: 'Remove MC-07'}));

    expect(screen.queryByRole('cell', {name: 'MC-07'})).not.toBeInTheDocument();
    expect(
      screen.getByText('6 stories · 2 done · 1 blocked')
    ).toBeInTheDocument();
  });
});
