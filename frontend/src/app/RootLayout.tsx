import { Message } from '@arco-design/web-react';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router';
import { onSessionExpired } from '@/api/http';
import { loginPath } from '@/features/auth/safeRedirect';
import { onSessionEnded } from '@/features/auth/useCurrentUser';
import { resetToVisitor } from '@/lib/queryClient';

export const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please sign in again.';

function showExpired() {
  Message.info({ id: 'session-expired', content: SESSION_EXPIRED_MESSAGE });
}

/**
 * Root route. A request rejected for a missing session needed one, so it leads to the login page.
 * A session found gone when the tab becomes active again just turns the page into the visitor view;
 * pages that need a session then send the visitor to sign in themselves.
 */
export function RootLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const returnTo = location.pathname + location.search;

  useEffect(
    () =>
      onSessionExpired(() => {
        if (location.pathname === '/login') return;
        showExpired();
        resetToVisitor(queryClient);
        void navigate(loginPath(returnTo), { replace: true });
      }),
    [location.pathname, navigate, queryClient, returnTo],
  );

  useEffect(
    () =>
      onSessionEnded(() => {
        showExpired();
        resetToVisitor(queryClient);
      }),
    [queryClient],
  );

  return <Outlet />;
}
