import { describe, expect, it } from 'vitest';
import type {
  AdminUser,
  AdminUserPage,
  AuditEntryPage,
  PasswordReset,
  UserProfile,
} from '@/api/generated/model';
import { apiGet, apiPatch, apiPost, signIn } from '@/test/api';
import { users } from '../db';

const bob = () => users.find((u) => u.username === 'bob')!;

describe('profile mock', () => {
  it('summarises a user for visitors', async () => {
    const profile: UserProfile = await (await apiGet('/api/users/Student')).json();
    expect(profile).toMatchObject({
      username: 'student',
      role: 'USER',
      solvedCount: 1,
      attemptedCount: 1,
      submissionCount: 3,
      acceptedCount: 1,
    });
    expect(profile.solved.map((p) => p.slug)).toEqual(['a-plus-b']);
  });

  it('hides unknown and disabled users from everyone but admins', async () => {
    expect((await apiGet('/api/users/nobody')).status).toBe(404);
    bob().enabled = false;
    expect((await apiGet('/api/users/bob')).status).toBe(404);
    await signIn('admin');
    expect((await apiGet('/api/users/bob')).status).toBe(200);
  });
});

describe('admin mock', () => {
  it('is for admins only', async () => {
    expect((await apiGet('/api/admin/users')).status).toBe(401);
    await signIn('alice');
    expect((await apiGet('/api/admin/users')).status).toBe(403);
  });

  it('lists, searches and filters accounts, newest first', async () => {
    await signIn('admin');
    const all: AdminUserPage = await (await apiGet('/api/admin/users')).json();
    expect(all.items.map((u) => u.username)).toEqual(['admin', 'bob', 'alice', 'student']);
    const search: AdminUserPage = await (await apiGet('/api/admin/users?q=AL')).json();
    expect(search.items.map((u) => u.username)).toEqual(['alice']);
    const admins: AdminUserPage = await (await apiGet('/api/admin/users?role=ADMIN')).json();
    expect(admins.items.map((u) => u.username)).toEqual(['admin']);
  });

  it('disables an account, ending its session, and records it', async () => {
    await signIn('admin');
    const updated: AdminUser = await (
      await apiPatch(`/api/admin/users/${bob().id}`, { enabled: false })
    ).json();
    expect(updated.enabled).toBe(false);
    expect(bob().epoch).toBe(1);

    const audit: AuditEntryPage = await (await apiGet('/api/admin/audit')).json();
    expect(audit.items[0]).toMatchObject({
      action: 'DISABLED',
      actor: { username: 'admin' },
      target: { username: 'bob' },
    });
  });

  it('refuses changes to the own account', async () => {
    await signIn('admin');
    const response = await apiPatch('/api/admin/users/4', { role: 'USER' });
    expect(response.status).toBe(409);
    expect((await apiPost('/api/admin/users/4/password-reset', {})).status).toBe(409);
  });

  it('resets a password to a temporary one', async () => {
    await signIn('admin');
    const reset: PasswordReset = await (
      await apiPost(`/api/admin/users/${bob().id}/password-reset`, {})
    ).json();
    expect(reset.temporaryPassword).toMatch(/^[A-HJ-NP-Za-km-z2-9]{16}$/);
    await apiPost('/api/auth/logout', {});
    await signIn('bob', reset.temporaryPassword);
    expect((await (await apiGet('/api/auth/me')).json()).mustChangePassword).toBe(true);
  });
});
