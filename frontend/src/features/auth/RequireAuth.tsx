import { Navigate, Outlet, useLocation } from 'react-router';
import { FullPageSpinner, PageError } from '@/components/PageState';
import { loginPath } from './safeRedirect';
import { useCurrentUser } from './useCurrentUser';

/** Layout route that only renders its children for a signed-in student. */
export function RequireAuth() {
  const location = useLocation();
  const { user, isPending, isSignedOut, refetch } = useCurrentUser();

  if (user) return <Outlet />;
  if (isPending) return <FullPageSpinner />;
  if (isSignedOut) {
    return <Navigate to={loginPath(location.pathname + location.search)} replace />;
  }
  return (
    <PageError
      title="Cannot reach wuOJ"
      description="Your session could not be checked."
      onRetry={() => void refetch()}
    />
  );
}
