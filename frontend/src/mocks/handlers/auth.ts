import { http, HttpResponse } from 'msw';
import type { CurrentUser, FieldError } from '@/api/generated/model';
import { getSessionUser, setSessionUser, users, type MockUser } from '../db';
import { checkCsrf, issueCsrfToken, latency, problemResponse } from './util';

function toCurrentUser(user: MockUser): CurrentUser {
  return { id: user.id, username: user.username, role: user.role };
}

export const authHandlers = [
  http.get('/api/auth/me', async () => {
    await latency();
    const token = issueCsrfToken();
    const headers = { 'Set-Cookie': `XSRF-TOKEN=${token}; Path=/` };
    const user = getSessionUser();
    if (!user) {
      return problemResponse(401, 'Unauthorized', 'Sign in to continue.', { headers });
    }
    return HttpResponse.json(toCurrentUser(user), { headers });
  }),

  http.post('/api/auth/login', async ({ request }) => {
    await latency();
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;

    const body = (await request.json().catch(() => ({}))) as {
      username?: unknown;
      password?: unknown;
    };
    const errors: FieldError[] = [];
    if (typeof body.username !== 'string' || body.username.length === 0) {
      errors.push({ field: 'username', message: 'must not be empty' });
    }
    if (typeof body.password !== 'string' || body.password.length === 0) {
      errors.push({ field: 'password', message: 'must not be empty' });
    }
    if (errors.length > 0) {
      return problemResponse(400, 'Bad Request', 'Invalid login request.', { errors });
    }

    const user = users.find((u) => u.username === body.username && u.password === body.password);
    if (!user) {
      return problemResponse(401, 'Unauthorized', 'Invalid username or password.');
    }
    setSessionUser(user);
    return HttpResponse.json(toCurrentUser(user));
  }),

  http.post('/api/auth/logout', async ({ request }) => {
    await latency();
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;
    setSessionUser(null);
    return new HttpResponse(null, { status: 204 });
  }),
];
