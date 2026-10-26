import { useEffect } from 'react';
import { APP_NAME } from './constants';

/** Sets the tab title to "<subject> · wuOJ", or just "wuOJ" without a subject. */
export function usePageTitle(subject?: string): void {
  useEffect(() => {
    document.title = subject ? `${subject} · ${APP_NAME}` : APP_NAME;
  }, [subject]);
}
