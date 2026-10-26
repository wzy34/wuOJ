export const DEFAULT_PATH = '/problems';

/**
 * Only follows in-app paths ("/x"), never protocol-relative ("//x") or absolute URLs, so a
 * crafted login link cannot send students to another site.
 */
export function safeRedirect(target: string | null | undefined): string {
  if (!target || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) {
    return DEFAULT_PATH;
  }
  return target;
}

export function loginPath(returnTo: string): string {
  return returnTo === DEFAULT_PATH ? '/login' : `/login?redirect=${encodeURIComponent(returnTo)}`;
}
