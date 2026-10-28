import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import {
  getCurrentUser,
  getGetCurrentUserQueryKey,
  useGetAuthOptions,
} from '@/api/generated/auth/auth';
import type { CurrentUser } from '@/api/generated/model';
import { getHttpStatus } from '@/api/http';

type SessionEndedListener = () => void;
const sessionEndedListeners = new Set<SessionEndedListener>();

/**
 * Subscribes to "a re-check found the signed-in user's session gone" (a password changed or an
 * admin disabled the account elsewhere); returns an unsubscribe function.
 */
export function onSessionEnded(listener: SessionEndedListener): () => void {
  sessionEndedListeners.add(listener);
  return () => sessionEndedListeners.delete(listener);
}

/** The signed-in user, or `null` for a visitor: a 401 is an answer here, not an error. */
async function fetchCurrentUser(
  queryClient: QueryClient,
  signal: AbortSignal,
): Promise<CurrentUser | null> {
  try {
    return await getCurrentUser(undefined, signal);
  } catch (error) {
    if (getHttpStatus(error) !== 401) throw error;
    if (queryClient.getQueryData(getGetCurrentUserQueryKey())) {
      sessionEndedListeners.forEach((listener) => listener());
    }
    return null;
  }
}

/**
 * The session check. The result is kept until sign-in, sign-out or session expiry replaces it,
 * and is checked again whenever the tab becomes active, so a session ended elsewhere is noticed.
 */
export function useCurrentUser() {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: getGetCurrentUserQueryKey(),
    queryFn: ({ signal }) => fetchCurrentUser(queryClient, signal),
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: 'always',
  });
  const user = query.data ?? undefined;
  return {
    ...query,
    user,
    isVisitor: query.data === null,
    isAdmin: user?.role === 'ADMIN',
  };
}

/** Whether visitors can register; asked once per page load. */
export function useRegistrationOpen(): boolean {
  const { data } = useGetAuthOptions({ query: { staleTime: Infinity, retry: false } });
  return data?.registrationEnabled ?? false;
}
