export const DEFAULT_PATH = '/problems';

/**
 * Only follows in-app paths ("/x"), never protocol-relative ("//x") or absolute URLs, so a
 * crafted login link cannot send people to another site.
 */
export function safeRedirect(target: string | null | undefined): string {
  if (!target || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) {
    return DEFAULT_PATH;
  }
  return target;
}

function withReturn(page: string, returnTo: string): string {
  return returnTo === DEFAULT_PATH ? page : `${page}?redirect=${encodeURIComponent(returnTo)}`;
}

export function loginPath(returnTo: string): string {
  return withReturn('/login', returnTo);
}

export function registerPath(returnTo: string): string {
  return withReturn('/register', returnTo);
}

export function settingsPath(returnTo: string): string {
  return withReturn('/settings', returnTo);
}
