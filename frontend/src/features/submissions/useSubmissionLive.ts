import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import type { SubmissionDetail, SubmissionStatusEvent } from '@/api/generated/model';
import {
  getGetSubmissionQueryKey,
  useGetSubmission,
} from '@/api/generated/submissions/submissions';

export const POLL_INTERVAL_MS = 2000;

function applyEvent(detail: SubmissionDetail, event: SubmissionStatusEvent): SubmissionDetail {
  return {
    ...detail,
    status: event.status,
    currentTest: event.currentTest,
    totalTests: event.totalTests ?? detail.totalTests,
    verdict: event.verdict,
    timeMs: event.timeMs,
    memoryKb: event.memoryKb,
  };
}

/** After a verdict: refresh the detail (test results, compiler output), lists and solved status. */
export function refreshAfterVerdict(queryClient: QueryClient, id: number): void {
  void queryClient.invalidateQueries({ queryKey: getGetSubmissionQueryKey(id) });
  void queryClient.invalidateQueries({
    predicate: ({ queryKey }) =>
      typeof queryKey[0] === 'string' &&
      (queryKey[0].startsWith('/api/problems') || queryKey[0] === '/api/submissions'),
  });
}

/**
 * A submission that updates itself until judged: status events arrive over SSE and are merged
 * into the cached detail. If the stream cannot be opened or drops, it falls back to fetching
 * every 2 seconds. Both stop once the verdict is final or the component unmounts.
 */
export function useSubmissionLive(id: number | undefined) {
  const queryClient = useQueryClient();
  const [streamFailedFor, setStreamFailedFor] = useState<number>();
  const polling = id !== undefined && streamFailedFor === id;

  const query = useGetSubmission(id ?? 0, {
    query: {
      enabled: id !== undefined,
      refetchInterval: (q) =>
        polling && q.state.data?.status !== 'FINISHED' ? POLL_INTERVAL_MS : false,
    },
  });

  const unfinished = query.data !== undefined && query.data.status !== 'FINISHED';
  const shouldStream = id !== undefined && unfinished && !polling;

  useEffect(() => {
    if (!shouldStream) return;
    const key = getGetSubmissionQueryKey(id);
    const source = new EventSource(`/api/submissions/${id}/events`);

    source.addEventListener('status', (message) => {
      const event = JSON.parse((message as MessageEvent<string>).data) as SubmissionStatusEvent;
      queryClient.setQueryData<SubmissionDetail>(key, (old) => old && applyEvent(old, event));
      if (event.status === 'FINISHED') {
        source.close();
        refreshAfterVerdict(queryClient, id);
      }
    });
    source.onerror = () => {
      // Without this close the browser would keep reconnecting; poll instead.
      source.close();
      setStreamFailedFor(id);
    };

    return () => source.close();
  }, [id, shouldStream, queryClient]);

  // Polling reached the verdict: refresh what depends on it, as the stream would have.
  const finishedWhilePolling = polling && query.data?.status === 'FINISHED';
  useEffect(() => {
    if (finishedWhilePolling && id !== undefined) refreshAfterVerdict(queryClient, id);
  }, [finishedWhilePolling, id, queryClient]);

  return query;
}
