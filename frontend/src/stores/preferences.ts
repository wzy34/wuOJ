import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export type ThemeChoice = 'light' | 'dark';

/** Storage key also read by the pre-paint script in index.html; keep both in sync. */
export const PREFERENCES_KEY = 'wuoj-preferences';

export const EDITOR_FONT_SIZES = [12, 13, 14, 16, 18, 20] as const;

type PreferencesState = {
  /** null until the student picks a theme; the app then follows the operating system. */
  theme: ThemeChoice | null;
  editorFontSize: number;
  setTheme: (theme: ThemeChoice) => void;
  setEditorFontSize: (size: number) => void;
};

/** Survives blocked storage (private mode, disabled cookies) by keeping preferences in memory. */
const safeLocalStorage = createJSONStorage(() => ({
  getItem: (name) => {
    try {
      return localStorage.getItem(name);
    } catch {
      return null;
    }
  },
  setItem: (name, value) => {
    try {
      localStorage.setItem(name, value);
    } catch {
      // Not persisted; the choice still applies for this visit.
    }
  },
  removeItem: (name) => {
    try {
      localStorage.removeItem(name);
    } catch {
      // Nothing to remove.
    }
  },
}));

export const usePreferences = create<PreferencesState>()(
  persist(
    (set) => ({
      theme: null,
      editorFontSize: 14,
      setTheme: (theme) => set({ theme }),
      setEditorFontSize: (editorFontSize) => set({ editorFontSize }),
    }),
    {
      name: PREFERENCES_KEY,
      storage: safeLocalStorage,
      partialize: ({ theme, editorFontSize }) => ({ theme, editorFontSize }),
    },
  ),
);
