/** Raw fetch helpers for exercising the mock API in tests (Node's fetch needs absolute URLs). */
export function apiUrl(path: string): string {
  return new URL(path, window.location.origin).toString();
}

function csrfToken(): string {
  return document.cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1] ?? '';
}

export async function apiGet(path: string): Promise<Response> {
  return fetch(apiUrl(path));
}

export async function apiPost(
  path: string,
  body: unknown,
  { csrf = true }: { csrf?: boolean } = {},
): Promise<Response> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (csrf) headers['X-XSRF-TOKEN'] = csrfToken();
  return fetch(apiUrl(path), { method: 'POST', headers, body: JSON.stringify(body) });
}

/** Fetches the CSRF cookie and signs in, like the app does on startup. */
export async function signIn(username = 'student', password = username): Promise<void> {
  await apiGet('/api/auth/me');
  const response = await apiPost('/api/auth/login', { username, password });
  if (!response.ok) throw new Error(`sign in failed: ${response.status}`);
}
