import { Message } from '@arco-design/web-react';
import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { getGetCurrentUserQueryKey } from '@/api/generated/auth/auth';

/**
 * Network failures and 5xx responses get a global notification. 4xx responses are expected
 * outcomes (not found, validation, session expiry, rate limiting) that pages handle themselves.
 */
export function describeUnexpectedError(error: unknown): string | undefined {
  if (!(error instanceof AxiosError)) return undefined;
  const status = error.response?.status;
  if (status === undefined) return 'Network error. Check your connection and try again.';
  if (status >= 500) return `The server had a problem (${status}). Please try again.`;
  return undefined;
}

function isClientError(error: unknown): boolean {
  const status = error instanceof AxiosError ? error.response?.status : undefined;
  return status !== undefined && status >= 400 && status < 500;
}

type Options = {
  notify?: (message: string) => void;
  retryDelay?: number;
};

export function createQueryClient({
  notify = (message) => Message.error(message),
  retryDelay,
}: Options = {}): QueryClient {
  const notifyIfUnexpected = (error: unknown) => {
    const message = describeUnexpectedError(error);
    if (message) notify(message);
  };

  return new QueryClient({
    queryCache: new QueryCache({ onError: notifyIfUnexpected }),
    mutationCache: new MutationCache({ onError: notifyIfUnexpected }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => !isClientError(error) && failureCount < 1,
        ...(retryDelay === undefined ? {} : { retryDelay }),
      },
      mutations: {
        retry: false,
      },
    },
  });
}

/**
 * After sign-out or an ended session: the page is now a visitor's. Everything cached for the
 * previous user is dropped, and the queries of mounted components fetch again as a visitor.
 */
export function resetToVisitor(queryClient: QueryClient): void {
  const session = getGetCurrentUserQueryKey();
  queryClient.setQueryData(session, null);
  queryClient.removeQueries({ type: 'inactive' });
  void queryClient.resetQueries({ predicate: ({ queryKey }) => queryKey[0] !== session[0] });
}
