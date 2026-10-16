import {render, screen} from '@testing-library/react';
import {I18nextProvider} from 'react-i18next';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {createI18n} from '../../i18n/i18n';
import {QuestionsCard} from './QuestionsCard';

describe('QuestionsCard', () => {
  beforeEach(() => {
    // A fixed "now": 16 Oct 2026, 10:00 in Stockholm. Never the wall clock.
    vi.useFakeTimers({toFake: ['Date']});
    vi.setSystemTime(new Date('2026-10-16T08:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('labels only questions past their needed-by day as overdue, in text', () => {
    render(
      <I18nextProvider i18n={createI18n('en')}>
        <QuestionsCard
          audience={null}
          questions={[
            {
              id: 'Q1',
              title: 'Late question',
              meta: '',
              audience: 'Kim',
              neededBy: '2026-10-15',
              status: 'question',
            },
            {
              id: 'Q2',
              title: 'Question due today',
              meta: '',
              audience: 'Sam',
              neededBy: '2026-10-16',
              status: 'question',
            },
          ]}
        />
      </I18nextProvider>
    );

    expect(screen.getAllByText('Overdue')).toHaveLength(1);
    expect(
      screen.getByText('Late question').closest('.question')
    ).toHaveTextContent('Overdue');
    expect(screen.getByText('Needed by 16 Oct')).toBeInTheDocument();
  });
});
