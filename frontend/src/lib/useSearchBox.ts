import { useEffect, useState } from 'react';

export const SEARCH_DELAY_MS = 300;

/**
 * A search box backed by a URL parameter: the box updates immediately, and `apply` (which writes
 * the URL) follows once typing pauses. When the URL changes from outside (back button, "Clear
 * search"), the box shows the new value.
 */
export function useSearchBox(urlValue: string, apply: (value: string) => void) {
  const [search, setSearch] = useState(urlValue);
  const [shown, setShown] = useState(urlValue);
  if (urlValue !== shown) {
    setShown(urlValue);
    setSearch(urlValue);
  }
  useEffect(() => {
    if (search.trim() === urlValue) return;
    const timer = setTimeout(() => apply(search.trim()), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [search, urlValue, apply]);
  return [search, setSearch] as const;
}
