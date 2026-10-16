import {describe, expect, it} from 'vitest';
import {isOverdue, sortQuestions, sortRisks} from './risksAndQuestions';

/** A fixed "now": 16 Oct 2026, 10:00 in Stockholm. Never the wall clock. */
const NOW = Date.parse('2026-10-16T08:00:00Z');

describe('sortRisks', () => {
  it('puts red first, then yellow, then green', () => {
    const risks = [
      {id: 'R1', level: 'green' as const},
      {id: 'R2', level: 'red' as const},
      {id: 'R3', level: 'yellow' as const},
    ];
    expect(sortRisks(risks).map(risk => risk.id)).toEqual(['R2', 'R3', 'R1']);
  });
});

describe('isOverdue', () => {
  it('is overdue only after the needed-by day has passed', () => {
    expect(isOverdue('2026-10-15', NOW)).toBe(true);
    expect(isOverdue('2026-10-16', NOW)).toBe(false);
    expect(isOverdue('2026-10-17', NOW)).toBe(false);
  });

  it('is never overdue without a needed-by date', () => {
    expect(isOverdue(null, NOW)).toBe(false);
  });
});

describe('sortQuestions', () => {
  it('puts overdue questions first, then the nearest date, and undated last', () => {
    const questions = [
      {id: 'Q1', neededBy: null, critical: false},
      {id: 'Q2', neededBy: '2026-10-30', critical: false},
      {id: 'Q3', neededBy: '2026-10-10', critical: false},
      {id: 'Q4', neededBy: '2026-10-20', critical: false},
    ];
    expect(sortQuestions(questions).map(question => question.id)).toEqual([
      'Q3',
      'Q4',
      'Q2',
      'Q1',
    ]);
  });

  it('puts critical questions first, even without a date', () => {
    const questions = [
      {id: 'dated', neededBy: '2026-10-10', critical: false},
      {id: 'critical-undated', neededBy: null, critical: true},
      {id: 'critical-dated', neededBy: '2026-10-20', critical: true},
      {id: 'undated', neededBy: null, critical: false},
    ];
    expect(sortQuestions(questions).map(question => question.id)).toEqual([
      'critical-dated',
      'critical-undated',
      'dated',
      'undated',
    ]);
  });
});
