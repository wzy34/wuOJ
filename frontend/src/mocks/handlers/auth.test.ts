import { describe, expect, it } from 'vitest';
import { apiGet, apiPost, signIn } from '@/test/api';

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
    expect(await me.json()).toEqual({ id: 1, username: 'student', role: 'STUDENT' });

    expect((await apiPost('/api/auth/logout', {})).status).toBe(204);
    expect((await apiGet('/api/auth/me')).status).toBe(401);
  });
});
