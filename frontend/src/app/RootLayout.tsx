import { Message } from '@arco-design/web-react';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router';
import { onSessionExpired } from '@/api/http';
import { loginPath } from '@/features/auth/safeRedirect';

export const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please sign in again.';

/** Root route: turns "session expired" signals from the HTTP layer into a trip to the login page. */
export function RootLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const returnTo = location.pathname + location.search;

  useEffect(
    () =>
      onSessionExpired(() => {
        if (location.pathname === '/login') return;
        Message.info({ id: 'session-expired', content: SESSION_EXPIRED_MESSAGE });
        queryClient.clear();
        void navigate(loginPath(returnTo), { replace: true });
      }),
    [location.pathname, navigate, queryClient, returnTo],
  );

  return <Outlet />;
}
