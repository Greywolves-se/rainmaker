import {daysUntil} from '../i18n/format';
import type {StatusCode} from './types';

type RiskLevel = 'red' | 'yellow' | 'green';

const RISK_ORDER: Record<RiskLevel, number> = {red: 0, yellow: 1, green: 2};

/** The pill for each risk level, so the level is shown as text and not only as colour. */
export const RISK_STATUS: Record<RiskLevel, StatusCode> = {
  red: 'riskRed',
  yellow: 'riskYellow',
  green: 'riskGreen',
};

/** Red first, then yellow, then green. Keeps the log order within a level. */
export function sortRisks<T extends {level: RiskLevel}>(
  risks: readonly T[]
): T[] {
  return [...risks].sort((a, b) => RISK_ORDER[a.level] - RISK_ORDER[b.level]);
}

/** True when the needed-by day (Stockholm time) has passed. */
export function isOverdue(neededBy: string | null, now: number): boolean {
  return neededBy !== null && daysUntil(neededBy, now) < 0;
}

/**
 * Critical questions first, even without a date (blockers such as "before start"). Within each
 * group the nearest needed-by date comes first, so overdue ones lead; undated ones come last.
 */
export function sortQuestions<
  T extends {neededBy: string | null; critical: boolean},
>(questions: readonly T[]): T[] {
  return [...questions].sort(
    (a, b) => Number(b.critical) - Number(a.critical) || byNeededBy(a, b)
  );
}

function byNeededBy(
  a: {neededBy: string | null},
  b: {neededBy: string | null}
): number {
  if (a.neededBy === b.neededBy) {
    return 0;
  }
  if (a.neededBy === null) {
    return 1;
  }
  if (b.neededBy === null) {
    return -1;
  }
  return a.neededBy.localeCompare(b.neededBy);
}
