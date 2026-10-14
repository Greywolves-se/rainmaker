import type {ReactNode} from 'react';
import {useTranslation} from 'react-i18next';

/**
 * An action from the mockup that has nowhere to go yet (the sample data has no URLs).
 * Shown disabled so nobody clicks a dead link.
 */
export function UnavailableAction({
  className = 'link-button',
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  const {t} = useTranslation();
  return (
    <button
      type="button"
      className={className}
      title={t('topBar.notAvailable')}
      disabled
    >
      {children}
    </button>
  );
}
