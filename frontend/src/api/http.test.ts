import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type InternalAxiosRequestConfig,
} from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  AXIOS_INSTANCE,
  customInstance,
  getApiProblem,
  getRetryAfterSeconds,
  onSessionExpired,
} from './http';

type Reply = { status: number; data?: unknown; headers?: Record<string, string> };

/** Adapter that answers every request with `reply`, rejecting non-2xx like axios's own `settle`. */
function respondWith(reply: Reply): AxiosAdapter {
  return async (config: InternalAxiosRequestConfig) => {
    const response = {
      data: reply.data,
      status: reply.status,
      statusText: '',
      headers: new AxiosHeaders(reply.headers ?? {}),
      config,
    };
    if (reply.status >= 200 && reply.status < 300) return response;
    throw new AxiosError('Request failed', AxiosError.ERR_BAD_REQUEST, config, null, response);
  };
}

const originalAdapter = AXIOS_INSTANCE.defaults.adapter;
let unsubscribe: (() => void) | undefined;

beforeEach(() => {
  unsubscribe = undefined;
});

afterEach(() => {
  AXIOS_INSTANCE.defaults.adapter = originalAdapter;
  unsubscribe?.();
});

describe('customInstance', () => {
  it('resolves to the response body', async () => {
    AXIOS_INSTANCE.defaults.adapter = respondWith({ status: 200, data: { id: 1 } });
    await expect(customInstance({ url: '/api/problems', method: 'GET' })).resolves.toEqual({
      id: 1,
    });
  });

  it('uses the Spring Security CSRF cookie and header names', () => {
    expect(AXIOS_INSTANCE.defaults.xsrfCookieName).toBe('XSRF-TOKEN');
    expect(AXIOS_INSTANCE.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN');
  });
});

describe('session expiry', () => {
  it('signals expiry when a protected request gets 401', async () => {
    const listener = vi.fn();
    unsubscribe = onSessionExpired(listener);
    AXIOS_INSTANCE.defaults.adapter = respondWith({ status: 401 });

    await expect(customInstance({ url: '/api/submissions', method: 'GET' })).rejects.toThrow();
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it.each(['/api/auth/me', '/api/auth/login'])('does not signal expiry for %s', async (url) => {
    const listener = vi.fn();
    unsubscribe = onSessionExpired(listener);
    AXIOS_INSTANCE.defaults.adapter = respondWith({ status: 401 });

    await expect(customInstance({ url, method: 'GET' })).rejects.toThrow();
    expect(listener).not.toHaveBeenCalled();
  });

  it('does not signal expiry for other errors', async () => {
    const listener = vi.fn();
    unsubscribe = onSessionExpired(listener);
    AXIOS_INSTANCE.defaults.adapter = respondWith({ status: 500 });

    await expect(customInstance({ url: '/api/submissions', method: 'GET' })).rejects.toThrow();
    expect(listener).not.toHaveBeenCalled();
  });
});

async function failWith(reply: Reply): Promise<unknown> {
  AXIOS_INSTANCE.defaults.adapter = respondWith(reply);
  try {
    await customInstance({ url: '/api/submissions', method: 'POST' });
  } catch (error) {
    return error;
  }
  throw new Error('expected the request to fail');
}

describe('getRetryAfterSeconds', () => {
  it('reads delta-seconds', async () => {
    const error = await failWith({ status: 429, headers: { 'Retry-After': '20' } });
    expect(getRetryAfterSeconds(error)).toBe(20);
  });

  it('reads an HTTP date', async () => {
    const now = Date.parse('2026-10-24T10:00:00Z');
    const error = await failWith({
      status: 429,
      headers: { 'Retry-After': 'Sat, 24 Oct 2026 10:00:30 GMT' },
    });
    expect(getRetryAfterSeconds(error, now)).toBe(30);
  });

  it('is undefined without the header or for other statuses', async () => {
    expect(getRetryAfterSeconds(await failWith({ status: 429 }))).toBeUndefined();
    expect(
      getRetryAfterSeconds(await failWith({ status: 400, headers: { 'Retry-After': '5' } })),
    ).toBeUndefined();
  });
});

describe('getApiProblem', () => {
  it('returns the problem details body', async () => {
    const body = { status: 400, title: 'Bad Request', detail: 'Invalid', errors: [] };
    expect(getApiProblem(await failWith({ status: 400, data: body }))).toEqual(body);
  });

  it('is undefined for non-problem bodies', async () => {
    expect(getApiProblem(await failWith({ status: 502, data: '<html>' }))).toBeUndefined();
  });
});
