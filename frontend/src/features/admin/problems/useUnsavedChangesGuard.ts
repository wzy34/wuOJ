import { Modal } from '@arco-design/web-react';
import { useEffect, useRef } from 'react';
import { useBlocker } from 'react-router';

/**
 * Asks before leaving the page while `dirty`: in-app navigation gets a confirmation dialog, and
 * reloading or closing the tab gets the browser's own prompt. Returns `allowLeave`, for navigating
 * away on purpose after a save.
 */
export function useUnsavedChangesGuard(dirty: boolean) {
  const leaving = useRef(false);
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty && !leaving.current && currentLocation.pathname !== nextLocation.pathname,
  );

  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    Modal.confirm({
      title: 'Leave without saving?',
      content: 'Your unsaved changes to this problem will be lost.',
      okText: 'Leave',
      cancelText: 'Stay',
      onOk: () => blocker.proceed(),
      onCancel: () => blocker.reset(),
    });
  }, [blocker]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  return {
    allowLeave: () => {
      leaving.current = true;
    },
  };
}
