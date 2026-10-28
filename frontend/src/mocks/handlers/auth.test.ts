import { describe, expect, it } from 'vitest';
import { apiGet, apiPost, signIn } from '@/test/api';
import { mockConfig, users } from '../db';

async function register(username: string, password: string) {
  await apiGet('/api/auth/me');
  return apiPost('/api/auth/register', { username, password });
}

describe('auth mock', () => {
  it('reports no session and issues the CSRF cookie', async () => {
    const response = await apiGet('/api/auth/me');
    expect(response.status).toBe(401);
    expect(response.headers.get('content-type')).toContain('application/problem+json');
    expect(document.cookie).toMatch(/XSRF-TOKEN=[\w-]+/);
  });

  it('rejects login without the CSRF header', async () => {
    await apiGet('/api/auth/me');
    const response = await apiPost(
      '/api/auth/login',
      { username: 'student', password: 'student' },
      { csrf: false },
    );
    expect(response.status).toBe(403);
  });

  it('rejects a wrong password', async () => {
    await apiGet('/api/auth/me');
    const response = await apiPost('/api/auth/login', { username: 'student', password: 'x' });
    expect(response.status).toBe(401);
    expect((await response.json()).detail).toBe('Invalid username or password.');
  });

  it('lists field errors for an empty username', async () => {
    await apiGet('/api/auth/me');
    const response = await apiPost('/api/auth/login', { username: '', password: 'x' });
    expect(response.status).toBe(400);
    expect((await response.json()).errors).toEqual([
      { field: 'username', message: 'must not be empty' },
    ]);
  });

  it('signs the demo student in and out', async () => {
    await signIn();
    const me = await apiGet('/api/auth/me');
    expect(me.status).toBe(200);
    expect(await me.json()).toEqual({
      id: 1,
      username: 'student',
      role: 'USER',
      mustChangePassword: false,
    });

    expect((await apiPost('/api/auth/logout', {})).status).toBe(204);
    expect((await apiGet('/api/auth/me')).status).toBe(401);
  });

  it('signs the demo admin in', async () => {
    await signIn('admin');
    expect((await (await apiGet('/api/auth/me')).json()).role).toBe('ADMIN');
  });

  it('refuses disabled accounts and ends their sessions', async () => {
    await signIn('bob');
    users.find((u) => u.username === 'bob')!.enabled = false;
    expect((await apiGet('/api/auth/me')).status).toBe(401);
    const login = await apiPost('/api/auth/login', { username: 'bob', password: 'bob' });
    expect(login.status).toBe(401);
  });
});

describe('registration mock', () => {
  it('reports whether registration is open', async () => {
    expect(await (await apiGet('/api/auth/options')).json()).toEqual({ registrationEnabled: true });
    mockConfig.registrationEnabled = false;
    expect(await (await apiGet('/api/auth/options')).json()).toEqual({
      registrationEnabled: false,
    });
    expect((await register('carol', 'correct horse battery')).status).toBe(403);
  });

  it('registers and signs in a new user', async () => {
    const response = await register('carol', 'correct horse battery');
    expect(response.status).toBe(201);
    expect(await response.json()).toMatchObject({ username: 'carol', role: 'USER' });
    expect((await (await apiGet('/api/auth/me')).json()).username).toBe('carol');
  });

  it.each([
    ['Alice', 'correct horse battery', 'username', 'is already taken'],
    ['Admin', 'correct horse battery', 'username', 'is reserved'],
    ['bad name', 'correct horse battery', 'username', expect.stringContaining('may only contain')],
    ['carol', 'short', 'password', 'must be at least 8 characters'],
    ['carol', 'password123', 'password', expect.stringContaining('too commonly used')],
    ['carolina1', 'Carolina1', 'password', 'must not be the same as the username'],
  ])('refuses %s / %s', async (username, password, field, message) => {
    const response = await register(username, password);
    expect(response.status).toBe(400);
    expect((await response.json()).errors).toEqual([{ field, message }]);
    expect(users.some((u) => u.username === 'carol')).toBe(false);
  });
});

describe('password change mock', () => {
  it('changes the password and keeps this session', async () => {
    await signIn('alice');
    const response = await apiPost('/api/account/password', {
      currentPassword: 'alice',
      newPassword: 'correct horse battery',
    });
    expect(response.status).toBe(204);
    expect((await apiGet('/api/auth/me')).status).toBe(200);
    await apiPost('/api/auth/logout', {});
    await expect(signIn('alice')).rejects.toThrow();
    await signIn('alice', 'correct horse battery');
  });

  it('reports a wrong current password and a weak new one', async () => {
    await signIn('alice');
    const wrong = await apiPost('/api/account/password', {
      currentPassword: 'x',
      newPassword: 'correct horse battery',
    });
    expect((await wrong.json()).errors).toEqual([
      { field: 'currentPassword', message: 'is not correct' },
    ]);
    const weak = await apiPost('/api/account/password', {
      currentPassword: 'alice',
      newPassword: 'password',
    });
    expect((await weak.json()).errors[0].field).toBe('newPassword');
  });

  it('holds an account that must change its password', async () => {
    users.find((u) => u.username === 'alice')!.mustChangePassword = true;
    await signIn('alice');
    expect((await (await apiGet('/api/auth/me')).json()).mustChangePassword).toBe(true);
    const held = await apiGet('/api/problems');
    expect(held.status).toBe(403);
    expect((await held.json()).detail).toBe('Change your password to continue.');

    await apiPost('/api/account/password', {
      currentPassword: 'alice',
      newPassword: 'correct horse battery',
    });
    expect((await apiGet('/api/problems')).status).toBe(200);
  });
});
