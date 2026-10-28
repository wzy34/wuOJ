import { useCallback, useEffect, useRef, useState } from 'react';
import { readStorage, removeStorage, writeStorage } from '@/lib/storage';
import { STARTER_TEMPLATE } from './starterTemplate';

const SAVE_DELAY_MS = 500;

/** Whose draft: a user's id, or the visitor's drafts in this browser. */
export type DraftOwner = number | 'guest';

const PREFIX = 'oj:draft:v1:';

export function draftKey(owner: DraftOwner, problemId: number): string {
  return `${PREFIX}${owner}:${problemId}:CPP17`;
}

/**
 * After signing in or registering: the visitor's drafts become the user's, for every problem the
 * user has no draft of their own for. Guest drafts are removed either way.
 */
export function adoptGuestDrafts(userId: number): void {
  const guestPrefix = `${PREFIX}guest:`;
  let keys: string[];
  try {
    keys = Object.keys(localStorage).filter((key) => key.startsWith(guestPrefix));
  } catch {
    return;
  }
  for (const guestKey of keys) {
    const userKey = PREFIX + userId + ':' + guestKey.slice(guestPrefix.length);
    const code = readStorage(guestKey);
    if (code !== null && readStorage(userKey) === null) writeStorage(userKey, code);
    removeStorage(guestKey);
  }
}

/**
 * Editor content saved in this browser per user (or guest) and problem, written shortly after
 * typing pauses and on leaving the page. Storage failures are ignored so editing never breaks.
 * Mount with `key={draftKey(...)}` so switching problems starts from that problem's draft.
 */
export function useDraft(owner: DraftOwner, problemId: number) {
  const key = draftKey(owner, problemId);
  const [code, setCode] = useState(() => readStorage(key) ?? STARTER_TEMPLATE);
  const latest = useRef({ key, code, dirty: false });

  useEffect(() => {
    latest.current = { key, code, dirty: latest.current.dirty };
  }, [key, code]);

  useEffect(() => {
    if (!latest.current.dirty) return;
    const timer = setTimeout(() => {
      writeStorage(key, code);
      latest.current.dirty = false;
    }, SAVE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [key, code]);

  // Leaving the page (navigation, session expiry, closing the tab) must not drop the last edits.
  useEffect(() => {
    const flush = () => {
      if (latest.current.dirty) writeStorage(latest.current.key, latest.current.code);
      latest.current.dirty = false;
    };
    window.addEventListener('pagehide', flush);
    return () => {
      window.removeEventListener('pagehide', flush);
      flush();
    };
  }, []);

  const update = useCallback((next: string) => {
    latest.current.dirty = true;
    setCode(next);
  }, []);

  const reset = useCallback(() => {
    latest.current.dirty = false;
    removeStorage(key);
    setCode(STARTER_TEMPLATE);
  }, [key]);

  return { code, setCode: update, reset };
}
