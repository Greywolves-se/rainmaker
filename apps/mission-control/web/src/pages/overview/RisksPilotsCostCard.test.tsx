import {render, screen} from '@testing-library/react';
import {I18nextProvider} from 'react-i18next';
import {describe, expect, it} from 'vitest';
import {SAMPLE_SNAPSHOT} from '../../data/sampleSnapshot';
import type {RiskItem} from '../../data/types';
import {createI18n} from '../../i18n/i18n';
import {RisksPilotsCostCard} from './RisksPilotsCostCard';

function risk(id: string, level: RiskItem['level']): RiskItem {
  return {id, title: `Risk ${id}`, level, owner: 'Kim', mitigation: 'Plan'};
}

function renderCard(risks: RiskItem[]) {
  return render(
    <I18nextProvider i18n={createI18n('en')}>
      <RisksPilotsCostCard overview={{...SAMPLE_SNAPSHOT.overview, risks}} />
    </I18nextProvider>
  );
}

describe('RisksPilotsCostCard', () => {
  it('shows three risks and says how many more there are', () => {
    renderCard([
      risk('A', 'red'),
      risk('B', 'red'),
      risk('C', 'yellow'),
      risk('D', 'green'),
      risk('E', 'green'),
    ]);

    expect(screen.getAllByText(/^Risk: Risk /)).toHaveLength(3);
    expect(screen.getByText('+2 more in the risk log')).toBeInTheDocument();
  });

  it('colours each risk by its level and names the level in text', () => {
    renderCard([risk('A', 'red'), risk('B', 'green')]);

    const green = screen.getByText('Risk: Risk B').closest('.callout');
    expect(green).toHaveClass('callout--green');
    expect(green).toHaveTextContent('Green');
    expect(screen.getByText('Risk: Risk A').closest('.callout')).toHaveClass(
      'callout--red'
    );
  });

  it('says so when there are no risks', () => {
    renderCard([]);

    expect(screen.getByText('No open risks')).toBeInTheDocument();
  });
});
