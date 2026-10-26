import { useEffect, useSyncExternalStore } from 'react';
import { usePreferences, type ThemeChoice } from '@/stores/preferences';

const DARK_QUERY = '(prefers-color-scheme: dark)';

function subscribeToSystemTheme(onChange: () => void): () => void {
  const media = window.matchMedia(DARK_QUERY);
  media.addEventListener('change', onChange);
  return () => media.removeEventListener('change', onChange);
}

function systemTheme(): ThemeChoice {
  return window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light';
}

/** The theme in effect: the student's choice, or the operating system's while there is none. */
export function useEffectiveTheme(): ThemeChoice {
  const choice = usePreferences((state) => state.theme);
  const system = useSyncExternalStore(subscribeToSystemTheme, systemTheme);
  return choice ?? system;
}

/** Applies the effective theme with Arco's documented `arco-theme` attribute on <body>. */
export function useApplyTheme(): void {
  const theme = useEffectiveTheme();
  useEffect(() => {
    if (theme === 'dark') document.body.setAttribute('arco-theme', 'dark');
    else document.body.removeAttribute('arco-theme');
  }, [theme]);
}
