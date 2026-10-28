import { http, HttpResponse } from 'msw';
import type {
  AdminUser,
  AuditEntry,
  PasswordReset,
  ProblemRef,
  UserProfile,
  UserRole,
} from '@/api/generated/model';
import { db, users, type MockUser } from '../db';
import {
  checkCsrf,
  latency,
  oneOf,
  paginate,
  parsePaging,
  problemResponse,
  requireAdmin,
  viewer,
} from './util';

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';

function toAdminUser(user: MockUser): AdminUser {
  const result: AdminUser = {
    id: user.id,
    username: user.username,
    role: user.role,
    enabled: user.enabled,
    mustChangePassword: user.mustChangePassword,
    createdAt: user.createdAt,
  };
  if (user.lastLoginAt) result.lastLoginAt = user.lastLoginAt;
  return result;
}

function toProfile(user: MockUser): UserProfile {
  const mine = db.submissions.filter((s) => s.user.id === user.id);
  const solved = new Map<number, { ref: ProblemRef; at: string }>();
  for (const s of [...mine].sort((a, b) => a.id - b.id)) {
    if (s.verdict === 'AC' && !solved.has(s.problem.id)) {
      solved.set(s.problem.id, { ref: s.problem, at: s.createdAt });
    }
  }
  const attempted = new Set(mine.map((s) => s.problem.id));
  return {
    id: user.id,
    username: user.username,
    role: user.role,
    joinedAt: user.createdAt,
    solvedCount: solved.size,
    attemptedCount: attempted.size - solved.size,
    submissionCount: mine.length,
    acceptedCount: mine.filter((s) => s.verdict === 'AC').length,
    solved: [...solved.values()].sort((a, b) => b.at.localeCompare(a.at)).map((s) => s.ref),
  };
}

function audit(actor: MockUser, target: MockUser, entry: Partial<(typeof db.audit)[number]>) {
  db.audit.push({
    id: db.audit.length + 1,
    createdAt: new Date().toISOString(),
    actorId: actor.id,
    targetId: target.id,
    action: 'ENABLED',
    ...entry,
  });
}

/** The target account, or a 404/409 response: admins cannot change their own account here. */
function otherAccount(admin: MockUser, id: number, ownAccount: string): MockUser | Response {
  const target = users.find((u) => u.id === id);
  if (!target) return problemResponse(404, 'Not Found', 'User not found.');
  if (target.id === admin.id) return problemResponse(409, 'Conflict', ownAccount);
  return target;
}

export const userHandlers = [
  http.get('/api/users/:username', async ({ params }) => {
    await latency();
    const reader = viewer();
    if (reader instanceof Response) return reader;
    const username = String(params.username).toLowerCase();
    const user = users.find((u) => u.username.toLowerCase() === username);
    if (!user || (!user.enabled && reader?.role !== 'ADMIN')) {
      return problemResponse(404, 'Not Found', 'User not found.');
    }
    return HttpResponse.json(toProfile(user));
  }),

  http.get('/api/admin/users', async ({ request }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const url = new URL(request.url);
    const paging = parsePaging(url);
    if (paging instanceof Response) return paging;

    const q = url.searchParams.get('q')?.trim().toLowerCase() ?? '';
    const roleParam = url.searchParams.get('role');
    const role = oneOf(roleParam, ['USER', 'ADMIN'] as const);
    const enabledParam = url.searchParams.get('enabled');
    if ((roleParam && !role) || (enabledParam && !['true', 'false'].includes(enabledParam))) {
      return problemResponse(400, 'Bad Request', 'Invalid filter.');
    }
    const matches = users
      .filter((u) => !q || u.username.toLowerCase().includes(q))
      .filter((u) => !role || u.role === role)
      .filter((u) => !enabledParam || String(u.enabled) === enabledParam)
      .sort((a, b) => b.id - a.id)
      .map(toAdminUser);
    return HttpResponse.json(paginate(matches, paging.page, paging.size));
  }),

  http.patch('/api/admin/users/:id', async ({ request, params }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;

    const body = (await request.json().catch(() => ({}))) as {
      role?: UserRole;
      enabled?: boolean;
    };
    if (body.role === undefined && body.enabled === undefined) {
      return problemResponse(400, 'Bad Request', 'The request is invalid.', {
        errors: [{ field: 'role', message: 'role or enabled must be given' }],
      });
    }
    const target = otherAccount(
      admin,
      Number(params.id),
      'You cannot change your own role or disable your own account.',
    );
    if (target instanceof Response) return target;

    const role = body.role ?? target.role;
    const enabled = body.enabled ?? target.enabled;
    if (role !== target.role) {
      audit(admin, target, { action: 'ROLE_CHANGED', fromRole: target.role, toRole: role });
    }
    if (enabled !== target.enabled)
      audit(admin, target, { action: enabled ? 'ENABLED' : 'DISABLED' });
    if (role !== target.role || enabled !== target.enabled) {
      target.role = role;
      target.enabled = enabled;
      target.epoch++;
    }
    return HttpResponse.json(toAdminUser(target));
  }),

  http.post('/api/admin/users/:id/password-reset', async ({ request, params }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;

    const target = otherAccount(
      admin,
      Number(params.id),
      'You cannot reset your own password. Change it in your settings instead.',
    );
    if (target instanceof Response) return target;

    const random = crypto.getRandomValues(new Uint32Array(16));
    target.password = Array.from(random, (n) => ALPHABET[n % ALPHABET.length]).join('');
    target.mustChangePassword = true;
    target.epoch++;
    audit(admin, target, { action: 'PASSWORD_RESET' });
    return HttpResponse.json<PasswordReset>({ temporaryPassword: target.password });
  }),

  http.get('/api/admin/audit', async ({ request }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const paging = parsePaging(new URL(request.url));
    if (paging instanceof Response) return paging;

    const ref = (id: number) => {
      const user = users.find((u) => u.id === id)!;
      return { id: user.id, username: user.username };
    };
    const entries: AuditEntry[] = [...db.audit]
      .sort((a, b) => b.id - a.id)
      .map(({ actorId, targetId, ...entry }) => ({
        ...entry,
        actor: ref(actorId),
        target: ref(targetId),
      }));
    return HttpResponse.json(paginate(entries, paging.page, paging.size));
  }),
];
