import { Navigate, Outlet, useLocation } from 'react-router';
import { FullPageSpinner, PageError } from '@/components/PageState';
import { settingsPath } from './safeRedirect';
import { useCurrentUser } from './useCurrentUser';

/**
 * Renders pages only once the session check has answered, so a signed-in user never sees the
 * visitor view first. While an admin-reset password must be changed, every page leads to settings.
 */
export function SessionGate() {
  const location = useLocation();
  const { user, isVisitor, isPending, refetch } = useCurrentUser();

  if (isPending) return <FullPageSpinner />;
  if (!user && !isVisitor) {
    return (
      <PageError
        title="Cannot reach wuOJ"
        description="Your session could not be checked."
        onRetry={() => void refetch()}
      />
    );
  }
  if (user?.mustChangePassword && location.pathname !== '/settings') {
    return <Navigate to={settingsPath(location.pathname + location.search)} replace />;
  }
  return <Outlet />;
}
