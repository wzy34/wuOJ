import { useQueryClient } from '@tanstack/react-query';
import { resetToVisitor } from '@/lib/queryClient';
import { useNavigate } from 'react-router';
import { useLogout } from '@/api/generated/auth/auth';

/** Ends the session, then drops every cached response so the next person starts clean as a visitor. */
export function useSignOut() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const logout = useLogout({
    mutation: {
      onSuccess: () => {
        resetToVisitor(queryClient);
        void navigate('/problems', { replace: true });
      },
    },
  });
  return { signOut: () => logout.mutate(), isPending: logout.isPending };
}
