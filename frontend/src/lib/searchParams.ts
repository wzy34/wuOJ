import { useCallback } from 'react';
import { useSearchParams } from 'react-router';

/** Reads a positive page number from the URL, defaulting to 1. */
export function readPage(params: URLSearchParams): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page > 0 ? page : 1;
}

/**
 * List state kept in the URL so reloads, shared links and the back button restore the view.
 * Changing anything but the page returns to page 1; empty values are dropped from the URL.
 */
export function useListSearchParams() {
  const [params, setParams] = useSearchParams();

  const update = useCallback(
    (changes: Record<string, string | number | undefined>, { replace = false } = {}) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          for (const [key, value] of Object.entries(changes)) {
            if (value === undefined || value === '' || (key === 'page' && value === 1)) {
              next.delete(key);
            } else {
              next.set(key, String(value));
            }
          }
          if (!('page' in changes)) next.delete('page');
          return next;
        },
        { replace },
      );
    },
    [setParams],
  );

  return { params, page: readPage(params), update };
}
