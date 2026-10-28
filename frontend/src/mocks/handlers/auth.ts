import { http, HttpResponse } from 'msw';
import type { AuthOptions, CurrentUser, FieldError } from '@/api/generated/model';
import { db, getSessionUser, mockConfig, setSessionUser, users, type MockUser } from '../db';
import { checkCsrf, issueCsrfToken, latency, problemResponse } from './util';

const USERNAME = /^[A-Za-z0-9._-]{1,64}$/;
const RESERVED = new Set(['admin', 'administrator', 'root', 'system', 'support', 'moderator']);
/** A small sample of the server's common-password list. */
const COMMON_PASSWORDS = new Set([
  'password',
  'password1',
  'password123',
  '12345678',
  '123456789',
  'qwerty123',
  'qwertyuiop',
  'iloveyou',
  'sunshine',
  'football',
]);

export function toCurrentUser(user: MockUser): CurrentUser {
  return {
    id: user.id,
    username: user.username,
    role: user.role,
    mustChangePassword: user.mustChangePassword,
  };
}

/** The server's password rules; returns the message for a field error, if any. */
export function passwordProblem(username: string, password: string): string | undefined {
  if (password.length < 8) return 'must be at least 8 characters';
  if (password.length > 128) return 'must be at most 128 characters';
  if (password.toLowerCase() === username.toLowerCase()) {
    return 'must not be the same as the username';
  }
  if (COMMON_PASSWORDS.has(password.toLowerCase())) {
    return 'is too commonly used; choose a less predictable password';
  }
  return undefined;
}

function fieldError(field: string, message: string) {
  return problemResponse(400, 'Bad Request', 'The request is invalid.', {
    errors: [{ field, message }],
  });
}

function signIn(user: MockUser) {
  user.lastLoginAt = new Date().toISOString();
  setSessionUser(user);
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

  http.get('/api/auth/options', async () => {
    await latency();
    return HttpResponse.json<AuthOptions>({ registrationEnabled: mockConfig.registrationEnabled });
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

    const username = (body.username as string).toLowerCase();
    const user = users.find(
      (u) => u.username.toLowerCase() === username && u.password === body.password && u.enabled,
    );
    if (!user) {
      return problemResponse(401, 'Unauthorized', 'Invalid username or password.');
    }
    signIn(user);
    return HttpResponse.json(toCurrentUser(user));
  }),

  http.post('/api/auth/register', async ({ request }) => {
    await latency();
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;
    if (!mockConfig.registrationEnabled) {
      return problemResponse(403, 'Forbidden', 'Registration is closed.');
    }

    const body = (await request.json().catch(() => ({}))) as {
      username?: unknown;
      password?: unknown;
    };
    const username = typeof body.username === 'string' ? body.username : '';
    const password = typeof body.password === 'string' ? body.password : '';
    if (!USERNAME.test(username)) {
      return fieldError(
        'username',
        "may only contain letters, digits, '.', '_' and '-' (at most 64 characters)",
      );
    }
    if (RESERVED.has(username.toLowerCase())) return fieldError('username', 'is reserved');
    if (users.some((u) => u.username.toLowerCase() === username.toLowerCase())) {
      return fieldError('username', 'is already taken');
    }
    const problem = passwordProblem(username, password);
    if (problem) return fieldError('password', problem);

    const user: MockUser = {
      id: db.nextUserId++,
      username,
      password,
      role: 'USER',
      enabled: true,
      mustChangePassword: false,
      epoch: 0,
      createdAt: new Date().toISOString(),
    };
    users.push(user);
    signIn(user);
    return HttpResponse.json(toCurrentUser(user), { status: 201 });
  }),

  http.post('/api/auth/logout', async ({ request }) => {
    await latency();
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;
    setSessionUser(null);
    return new HttpResponse(null, { status: 204 });
  }),

  http.post('/api/account/password', async ({ request }) => {
    await latency();
    const user = getSessionUser();
    if (!user) return problemResponse(401, 'Unauthorized', 'Sign in to continue.');
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;

    const body = (await request.json().catch(() => ({}))) as {
      currentPassword?: unknown;
      newPassword?: unknown;
    };
    if (body.currentPassword !== user.password) {
      return fieldError('currentPassword', 'is not correct');
    }
    const newPassword = typeof body.newPassword === 'string' ? body.newPassword : '';
    const problem = passwordProblem(user.username, newPassword);
    if (problem) return fieldError('newPassword', problem);

    user.password = newPassword;
    user.mustChangePassword = false;
    user.epoch++;
    // Other sessions end; this one moves to the new epoch.
    setSessionUser(user);
    return new HttpResponse(null, { status: 204 });
  }),
];
