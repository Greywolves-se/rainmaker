import {render, screen} from '@testing-library/react';
import {I18nextProvider} from 'react-i18next';
import {describe, expect, it} from 'vitest';
import {SAMPLE_SNAPSHOT} from '../data/sampleSnapshot';
import {createI18n} from '../i18n/i18n';
import {KpiCards} from './OverviewPage';

describe('KpiCards', () => {
  it('colours an "at risk" KPI amber, not green', () => {
    const overview = {
      ...SAMPLE_SNAPSHOT.overview,
      project: {status: 'atRisk' as const, sprint: 'Sprint 2', percentDone: 75},
    };

    render(
      <I18nextProvider i18n={createI18n('en')}>
        <KpiCards overview={overview} />
      </I18nextProvider>
    );

    const value = screen.getByText('At risk');
    expect(value).toHaveClass('kpi__value--amber');
    expect(value).not.toHaveClass('kpi__value--green');
    expect(value.querySelector('.dot')).toHaveClass('dot--amber');
  });

  it('shows "No data yet" for a KPI the snapshot does not have', () => {
    const overview = {...SAMPLE_SNAPSHOT.overview, operations: null};

    render(
      <I18nextProvider i18n={createI18n('en')}>
        <KpiCards overview={overview} />
      </I18nextProvider>
    );

    const card = screen.getByRole('heading', {
      name: 'Operations',
    }).parentElement;
    expect(card).toHaveTextContent('No data yet');
  });
});
