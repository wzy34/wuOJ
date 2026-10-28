import { Navigate, Outlet, useLocation } from 'react-router';
import { NotFoundPage } from '@/app/NotFoundPage';
import { loginPath } from './safeRedirect';
import { useCurrentUser } from './useCurrentUser';

/** Layout route for pages that need a signed-in user; visitors are sent to sign in first. */
export function RequireAuth() {
  const location = useLocation();
  const { user } = useCurrentUser();
  if (!user) return <Navigate to={loginPath(location.pathname + location.search)} replace />;
  return <Outlet />;
}

/** Layout route for admin pages: visitors sign in first, other users get "page not found". */
export function RequireAdmin() {
  const location = useLocation();
  const { user, isAdmin } = useCurrentUser();
  if (!user) return <Navigate to={loginPath(location.pathname + location.search)} replace />;
  if (!isAdmin) return <NotFoundPage />;
  return <Outlet />;
}
