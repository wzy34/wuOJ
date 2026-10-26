import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { STARTER_TEMPLATE } from './starterTemplate';
import { draftKey, useDraft } from './useDraft';

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('useDraft', () => {
  it('starts from the template and restores a saved draft', () => {
    vi.useFakeTimers();
    const first = renderHook(() => useDraft(1, 7));
    expect(first.result.current.code).toBe(STARTER_TEMPLATE);

    act(() => first.result.current.setCode('int main() { return 1; }'));
    act(() => vi.advanceTimersByTime(500));
    first.unmount();

    const again = renderHook(() => useDraft(1, 7));
    expect(again.result.current.code).toBe('int main() { return 1; }');
  });

  it('saves pending edits when the page is left before the delay', () => {
    const { result, unmount } = renderHook(() => useDraft(1, 7));
    act(() => result.current.setCode('quick edit'));
    unmount();
    expect(localStorage.getItem(draftKey(1, 7))).toBe('quick edit');
  });

  it('keeps drafts per user', () => {
    localStorage.setItem(draftKey(1, 7), "student A's code");
    const { result } = renderHook(() => useDraft(2, 7));
    expect(result.current.code).toBe(STARTER_TEMPLATE);
  });

  it('resets to the template and forgets the draft', () => {
    localStorage.setItem(draftKey(1, 7), 'old');
    const { result } = renderHook(() => useDraft(1, 7));
    act(() => result.current.reset());
    expect(result.current.code).toBe(STARTER_TEMPLATE);
    expect(localStorage.getItem(draftKey(1, 7))).toBeNull();
  });

  it('keeps editing when storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const { result, unmount } = renderHook(() => useDraft(1, 7));
    expect(result.current.code).toBe(STARTER_TEMPLATE);
    act(() => result.current.setCode('still works'));
    expect(result.current.code).toBe('still works');
    expect(() => unmount()).not.toThrow();
  });
});
