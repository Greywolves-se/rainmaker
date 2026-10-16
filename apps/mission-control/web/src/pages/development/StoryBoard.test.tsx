import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type {ReactNode} from 'react';
import {I18nextProvider} from 'react-i18next';
import {describe, expect, it} from 'vitest';
import type {Snapshot, Story} from '../../data/types';
import {createI18n} from '../../i18n/i18n';
import {StoryBoard} from './StoryBoard';

const SPRINT: NonNullable<Snapshot['development']['sprint']> = {
  name: 'Sprint 4',
  startDate: '2026-10-26',
  endDate: '2026-11-06',
  url: 'https://github.com/example-org/example-repo/milestone/4',
};

const story = (overrides: Partial<Story>): Story => ({
  id: '1',
  title: 'Story',
  assignees: [],
  labels: [],
  status: 'todo',
  inCurrentSprint: true,
  createdAt: '2026-10-20T09:00:00Z',
  url: null,
  ...overrides,
});

const STORIES: Story[] = [
  story({
    id: '101',
    title: 'Load the snapshot',
    assignees: ['Alex'],
    labels: ['area: web'],
    status: 'inProgress',
    url: 'https://github.com/example-org/example-repo/issues/101',
  }),
  story({
    id: '102',
    title: 'GitHub source',
    labels: ['area: collector'],
    status: 'blocked',
  }),
  story({
    id: '97',
    title: 'Collector skeleton',
    assignees: ['Sam'],
    labels: ['area: collector'],
    status: 'done',
    inCurrentSprint: false,
  }),
];

const wrap = (node: ReactNode) => (
  <I18nextProvider i18n={createI18n('en')}>{node}</I18nextProvider>
);

const rowTitles = () =>
  within(screen.getAllByRole('rowgroup')[1]!)
    .queryAllByRole('row')
    .map(row => within(row).getAllByRole('cell')[1]!.textContent);

describe('StoryBoard', () => {
  it('shows the sprint header with dates and progress', () => {
    render(wrap(<StoryBoard sprint={SPRINT} stories={STORIES} />));

    expect(screen.getByRole('heading', {level: 2})).toHaveTextContent(
      'Sprint 4 · 26 Oct – 6 Nov'
    );
    expect(screen.getByText('2 stories · 0 done · 1 blocked')).toBeVisible();
    expect(screen.getByText('0% done')).toBeVisible();
  });

  it('lists current-sprint stories read-only, each linking to GitHub', () => {
    render(wrap(<StoryBoard sprint={SPRINT} stories={STORIES} />));

    expect(rowTitles()).toEqual(['Load the snapshot', 'GitHub source']);
    expect(
      screen.getByRole('link', {name: 'Load the snapshot'})
    ).toHaveAttribute(
      'href',
      'https://github.com/example-org/example-repo/issues/101'
    );
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('filters by assignee, label and current sprint', async () => {
    render(wrap(<StoryBoard sprint={SPRINT} stories={STORIES} />));

    await userEvent.click(screen.getByLabelText('Only the current sprint'));
    expect(rowTitles()).toEqual([
      'Load the snapshot',
      'GitHub source',
      'Collector skeleton',
    ]);

    await userEvent.selectOptions(screen.getByLabelText('Assignee'), 'Sam');
    expect(rowTitles()).toEqual(['Collector skeleton']);

    await userEvent.selectOptions(screen.getByLabelText('Assignee'), '');
    await userEvent.selectOptions(screen.getByLabelText('Label'), 'area: web');
    expect(rowTitles()).toEqual(['Load the snapshot']);
  });

  it('can be used with the keyboard', async () => {
    render(wrap(<StoryBoard sprint={SPRINT} stories={STORIES} />));

    await userEvent.tab();
    expect(screen.getByLabelText('Assignee')).toHaveFocus();
    await userEvent.tab();
    await userEvent.tab();
    expect(screen.getByLabelText('Only the current sprint')).toHaveFocus();
    await userEvent.keyboard(' ');
    expect(rowTitles()).toHaveLength(3);
  });

  it('says the sprint is empty when no filter is set', async () => {
    render(wrap(<StoryBoard sprint={null} stories={[]} />));

    expect(screen.getByRole('heading', {level: 2})).toHaveTextContent(
      'No active sprint'
    );
    expect(screen.getByText('No stories in this sprint')).toBeVisible();

    await userEvent.click(screen.getByLabelText('Only the current sprint'));
    expect(screen.getByText('No stories yet')).toBeVisible();
  });

  it('says no story matches only when a filter is set', async () => {
    render(wrap(<StoryBoard sprint={SPRINT} stories={STORIES} />));

    await userEvent.selectOptions(screen.getByLabelText('Assignee'), 'Alex');
    await userEvent.selectOptions(
      screen.getByLabelText('Label'),
      'area: collector'
    );

    expect(screen.getByText('No stories match the filters')).toBeVisible();
  });

  it('resets a filter whose choice is gone after a snapshot refresh', async () => {
    const {rerender} = render(
      wrap(<StoryBoard sprint={SPRINT} stories={STORIES} />)
    );
    await userEvent.selectOptions(screen.getByLabelText('Assignee'), 'Alex');

    rerender(
      wrap(
        <StoryBoard
          sprint={SPRINT}
          stories={[story({id: '104', title: 'New', assignees: ['Kim']})]}
        />
      )
    );

    expect(screen.getByLabelText('Assignee')).toHaveDisplayValue('All');
    expect(rowTitles()).toEqual(['New']);
  });

  it('offers only the people the sprint toggle allows', async () => {
    render(wrap(<StoryBoard sprint={SPRINT} stories={STORIES} />));
    const options = () =>
      within(screen.getByLabelText('Assignee'))
        .getAllByRole('option')
        .map(option => option.textContent);

    // Sam only has a backlog item, so is not offered while the sprint toggle is on.
    expect(options()).toEqual(['All', 'Alex']);

    await userEvent.click(screen.getByLabelText('Only the current sprint'));
    expect(options()).toEqual(['All', 'Alex', 'Sam']);
  });

  it('uses the singular for one story', () => {
    render(wrap(<StoryBoard sprint={SPRINT} stories={[story({})]} />));

    expect(screen.getByText('1 story · 0 done · 0 blocked')).toBeVisible();
  });

  it('follows the snapshot instead of keeping its own copy', () => {
    const {rerender} = render(
      wrap(<StoryBoard sprint={SPRINT} stories={STORIES} />)
    );

    rerender(
      wrap(
        <StoryBoard
          sprint={SPRINT}
          stories={[story({id: '104', title: 'New from GitHub'})]}
        />
      )
    );

    expect(rowTitles()).toEqual(['New from GitHub']);
  });
});
