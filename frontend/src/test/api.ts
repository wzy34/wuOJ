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
  { csrf = true, method = 'POST' }: { csrf?: boolean; method?: string } = {},
): Promise<Response> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (csrf) headers['X-XSRF-TOKEN'] = csrfToken();
  return fetch(apiUrl(path), { method, headers, body: JSON.stringify(body) });
}

export async function apiPatch(path: string, body: unknown): Promise<Response> {
  return apiPost(path, body, { method: 'PATCH' });
}

export async function apiPut(path: string, body: unknown): Promise<Response> {
  return apiPost(path, body, { method: 'PUT' });
}

/** A multipart POST, as the generated client sends one: JSON fields as text, files as `files`. */
export async function apiForm(
  path: string,
  fields: Record<string, unknown>,
  files: (File | string)[] = [],
): Promise<Response> {
  const form = new FormData();
  for (const [name, value] of Object.entries(fields)) form.append(name, JSON.stringify(value));
  for (const file of files)
    form.append('files', typeof file === 'string' ? new File([file], 'upload') : file);
  return fetch(apiUrl(path), {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': csrfToken() },
    body: form,
  });
}

/** Fetches the CSRF cookie and signs in, like the app does on startup. */
export async function signIn(username = 'student', password = username): Promise<void> {
  await apiGet('/api/auth/me');
  const response = await apiPost('/api/auth/login', { username, password });
  if (!response.ok) throw new Error(`sign in failed: ${response.status}`);
}
