import { useGetCurrentUser } from '@/api/generated/auth/auth';
import { getHttpStatus } from '@/api/http';

/**
 * The session check. A 401 means "signed out" and is not retried; the result is kept until
 * sign-in, sign-out or session expiry replaces or clears it.
 */
export function useCurrentUser() {
  const query = useGetCurrentUser({
    query: { retry: false, staleTime: Infinity },
  });
  return {
    ...query,
    user: query.data,
    isSignedOut: getHttpStatus(query.error) === 401,
  };
}
