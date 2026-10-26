import { act, render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AppProviders } from '@/app/AppProviders';
import { createQueryClient } from '@/lib/queryClient';
import { PREFERENCES_KEY, usePreferences } from '@/stores/preferences';
import { setSystemDark } from '@/test/matchMedia';
import html from '../../index.html?raw';

const isDark = () => document.body.getAttribute('arco-theme') === 'dark';

function renderProviders() {
  return render(<AppProviders queryClient={createQueryClient()}>{null}</AppProviders>);
}

describe('theme', () => {
  it('follows the operating system until the student chooses', () => {
    renderProviders();
    expect(isDark()).toBe(false);

    act(() => setSystemDark(true));
    expect(isDark()).toBe(true);

    act(() => setSystemDark(false));
    expect(isDark()).toBe(false);
  });

  it('keeps an explicit choice over the system setting and remembers it', () => {
    setSystemDark(true);
    renderProviders();
    expect(isDark()).toBe(true);

    act(() => usePreferences.getState().setTheme('light'));
    expect(isDark()).toBe(false);
    expect(JSON.parse(localStorage.getItem(PREFERENCES_KEY)!).state.theme).toBe('light');

    act(() => setSystemDark(true));
    expect(isDark()).toBe(false);
  });
});

describe('pre-paint theme script', () => {
  const script = /<script>([\s\S]*?)<\/script>/.exec(html)![1];
  const runScript = () => new Function(script)();

  it('applies a remembered light choice even when the system is dark', () => {
    setSystemDark(true);
    localStorage.setItem(PREFERENCES_KEY, JSON.stringify({ state: { theme: 'light' } }));
    runScript();
    expect(isDark()).toBe(false);
  });

  it('applies a remembered dark choice', () => {
    localStorage.setItem(PREFERENCES_KEY, JSON.stringify({ state: { theme: 'dark' } }));
    runScript();
    expect(isDark()).toBe(true);
  });

  it('falls back to the system theme without a choice', () => {
    setSystemDark(true);
    runScript();
    expect(isDark()).toBe(true);
  });
});
