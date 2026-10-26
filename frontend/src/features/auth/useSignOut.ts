import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { useLogout } from '@/api/generated/auth/auth';

/** Ends the session, then drops every cached response so the next student starts clean. */
export function useSignOut() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const logout = useLogout({
    mutation: {
      onSuccess: () => {
        queryClient.clear();
        void navigate('/login', { replace: true });
      },
    },
  });
  return { signOut: () => logout.mutate(), isPending: logout.isPending };
}
