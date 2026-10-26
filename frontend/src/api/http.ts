import Axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosRequestConfig,
  type RawAxiosResponseHeaders,
} from 'axios';
import type { ApiProblem } from './generated/model';

/**
 * Shared axios instance. Requests go to the same origin (`/api/...` paths come from the contract),
 * so the session cookie is sent automatically and axios copies the `XSRF-TOKEN` cookie into the
 * `X-XSRF-TOKEN` header, matching Spring Security's cookie CSRF repository.
 */
export const AXIOS_INSTANCE = Axios.create({
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

// Requests whose 401 is an expected answer rather than an expired session.
const SESSION_PROBES = new Set(['/api/auth/me', '/api/auth/login']);

type SessionExpiredListener = () => void;
const sessionExpiredListeners = new Set<SessionExpiredListener>();

/** Subscribes to "the session is no longer valid" signals; returns an unsubscribe function. */
export function onSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

AXIOS_INSTANCE.interceptors.response.use(undefined, (error: unknown) => {
  if (
    error instanceof AxiosError &&
    error.response?.status === 401 &&
    !SESSION_PROBES.has(error.config?.url ?? '')
  ) {
    sessionExpiredListeners.forEach((listener) => listener());
  }
  return Promise.reject(error);
});

/** orval mutator: every generated request function goes through here and resolves to the body. */
export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => AXIOS_INSTANCE({ ...config, ...options }).then(({ data }) => data as T);

export type ErrorType<Error> = AxiosError<Error>;
export type BodyType<BodyData> = BodyData;

/** The RFC 9457 body of a failed request, if the server sent one. */
export function getApiProblem(error: unknown): ApiProblem | undefined {
  if (!(error instanceof AxiosError)) return undefined;
  const data: unknown = error.response?.data;
  if (data && typeof data === 'object' && 'status' in data && 'title' in data) {
    return data as ApiProblem;
  }
  return undefined;
}

export function getHttpStatus(error: unknown): number | undefined {
  return error instanceof AxiosError ? error.response?.status : undefined;
}

/**
 * Seconds from a 429 response's `Retry-After` header, which may be delta-seconds or an HTTP date.
 * Returns undefined when the error is not a 429 or the header is missing or unparseable.
 */
export function getRetryAfterSeconds(error: unknown, now: number = Date.now()): number | undefined {
  if (!(error instanceof AxiosError) || error.response?.status !== 429) return undefined;
  const raw: unknown = AxiosHeaders.from(
    error.response.headers as RawAxiosResponseHeaders & Record<string, string>,
  ).get('retry-after');
  if (typeof raw !== 'string' || raw.trim() === '') return undefined;
  if (/^\d+$/.test(raw.trim())) return Number(raw.trim());
  const date = Date.parse(raw);
  if (Number.isNaN(date)) return undefined;
  return Math.max(0, Math.ceil((date - now) / 1000));
}
