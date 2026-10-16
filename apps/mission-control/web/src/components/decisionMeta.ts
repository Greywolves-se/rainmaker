import type {TFunction} from 'i18next';
import type {DecisionItem} from '../data/types';
import {formatDay} from '../i18n/format';

/** "Simon · 2 Oct · Section 6; D17" – the line under a decision's title on both pages. */
export function decisionMeta(
  decision: DecisionItem,
  t: TFunction,
  language: string
): string {
  const values = {
    owner: decision.owner,
    date: formatDay(decision.date, language),
    reference: decision.reference,
  };
  return decision.reference
    ? t('decisions.metaWithReference', values)
    : t('decisions.meta', values);
}
