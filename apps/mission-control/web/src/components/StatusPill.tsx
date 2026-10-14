import {useTranslation} from 'react-i18next';
import {STATUS_TONE} from '../data/statusTone';
import type {StatusCode} from '../data/types';

export function StatusPill({status}: {status: StatusCode}) {
  const {t} = useTranslation();
  return (
    <span className={`pill pill--${STATUS_TONE[status]}`}>
      {t(`status.${status}`)}
    </span>
  );
}
