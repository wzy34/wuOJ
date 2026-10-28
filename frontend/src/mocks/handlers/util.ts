import { delay, HttpResponse } from 'msw';
import type { ApiProblem, FieldError } from '@/api/generated/model';
import { db, getSessionUser, mockConfig, type MockUser } from '../db';

export function problemResponse(
  status: number,
  title: string,
  detail: string,
  extra: { errors?: FieldError[]; headers?: Record<string, string> } = {},
) {
  const body: ApiProblem = { type: 'about:blank', status, title, detail };
  if (extra.errors) body.errors = extra.errors;
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json', ...extra.headers },
  });
}

export function latency() {
  return delay(mockConfig.latencyMs);
}

/**
 * Who the request is answered for: the signed-in user, `undefined` for a visitor, or a 403 to return
 * as-is while the user must change their password (as the real server holds such accounts).
 */
export function viewer(): MockUser | undefined | Response {
  const user = getSessionUser();
  if (user?.mustChangePassword) {
    return problemResponse(403, 'Forbidden', 'Change your password to continue.');
  }
  return user;
}

/** The signed-in user, or a 401 (or 403) response to return as-is. */
export function requireUser(): MockUser | Response {
  return viewer() ?? problemResponse(401, 'Unauthorized', 'Sign in to continue.');
}

/** The signed-in admin, or a 401/403 response to return as-is. */
export function requireAdmin(): MockUser | Response {
  const user = requireUser();
  if (user instanceof Response || user.role === 'ADMIN') return user;
  return problemResponse(403, 'Forbidden', 'You are not allowed to do this.');
}

/** A 403 response when the CSRF header does not match the issued token, else undefined. */
export function checkCsrf(request: Request): Response | undefined {
  const header = request.headers.get('X-XSRF-TOKEN');
  if (!db.csrfToken || header !== db.csrfToken) {
    return problemResponse(403, 'Forbidden', 'Missing or invalid CSRF token.');
  }
  return undefined;
}

/** Issues the XSRF-TOKEN cookie, as the real server does on GET /api/auth/me. */
export function issueCsrfToken(): string {
  db.csrfToken ??= crypto.randomUUID();
  // Handlers run in the page (or jsdom), so the cookie is visible to axios immediately.
  if (typeof document !== 'undefined') {
    document.cookie = `XSRF-TOKEN=${db.csrfToken}; path=/; SameSite=Lax`;
  }
  return db.csrfToken;
}

export function parsePaging(url: URL): { page: number; size: number } | Response {
  const page = Number(url.searchParams.get('page') ?? 1);
  const size = Number(url.searchParams.get('size') ?? 20);
  const errors: FieldError[] = [];
  if (!Number.isInteger(page) || page < 1) errors.push({ field: 'page', message: 'must be ≥ 1' });
  if (!Number.isInteger(size) || size < 1 || size > 100) {
    errors.push({ field: 'size', message: 'must be between 1 and 100' });
  }
  if (errors.length > 0) {
    return problemResponse(400, 'Bad Request', 'Invalid paging parameters.', { errors });
  }
  return { page, size };
}

export function paginate<T>(items: T[], page: number, size: number) {
  return {
    items: items.slice((page - 1) * size, page * size),
    page,
    size,
    totalItems: items.length,
    totalPages: Math.ceil(items.length / size),
  };
}

export function oneOf<T extends string>(
  value: string | null,
  allowed: readonly T[],
): T | undefined {
  return allowed.includes(value as T) ? (value as T) : undefined;
}
