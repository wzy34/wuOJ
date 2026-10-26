import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { createQueryClient, describeUnexpectedError } from './queryClient';

function httpError(status?: number): AxiosError {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
  const response =
    status === undefined
      ? undefined
      : { status, statusText: '', data: {}, headers: new AxiosHeaders(), config };
  return new AxiosError('failed', 'ERR', config, null, response);
}

describe('createQueryClient', () => {
  it('retries a 500 once and shows a single notification', async () => {
    const notify = vi.fn();
    const client = createQueryClient({ notify, retryDelay: 0 });
    const queryFn = vi.fn().mockRejectedValue(httpError(500));

    await expect(client.fetchQuery({ queryKey: ['x'], queryFn })).rejects.toThrow();

    expect(queryFn).toHaveBeenCalledTimes(2);
    expect(notify).toHaveBeenCalledTimes(1);
    expect(notify).toHaveBeenCalledWith('The server had a problem (500). Please try again.');
  });

  it('does not retry or notify on a 404', async () => {
    const notify = vi.fn();
    const client = createQueryClient({ notify, retryDelay: 0 });
    const queryFn = vi.fn().mockRejectedValue(httpError(404));

    await expect(client.fetchQuery({ queryKey: ['y'], queryFn })).rejects.toThrow();

    expect(queryFn).toHaveBeenCalledTimes(1);
    expect(notify).not.toHaveBeenCalled();
  });

  it('notifies once for a failed mutation without retrying', async () => {
    const notify = vi.fn();
    const client = createQueryClient({ notify, retryDelay: 0 });
    const mutationFn = vi.fn().mockRejectedValue(httpError());

    const mutation = client.getMutationCache().build(client, { mutationFn });
    await expect(mutation.execute(undefined)).rejects.toThrow();

    expect(mutationFn).toHaveBeenCalledTimes(1);
    expect(notify).toHaveBeenCalledWith('Network error. Check your connection and try again.');
  });

  it('uses the agreed defaults', () => {
    const defaults = createQueryClient().getDefaultOptions().queries;
    expect(defaults?.staleTime).toBe(30_000);
    expect(defaults?.refetchOnWindowFocus).toBe(false);
  });
});

describe('describeUnexpectedError', () => {
  it('ignores client errors and non-HTTP errors', () => {
    expect(describeUnexpectedError(httpError(400))).toBeUndefined();
    expect(describeUnexpectedError(httpError(429))).toBeUndefined();
    expect(describeUnexpectedError(new Error('boom'))).toBeUndefined();
  });
});
