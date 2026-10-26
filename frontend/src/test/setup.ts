import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { resetDb, resetMockConfig } from '@/mocks/db';
import { server } from '@/mocks/node';
import { usePreferences } from '@/stores/preferences';
import { setSystemDark, setViewportWidth } from './matchMedia';

// jsdom cannot run Monaco; see monacoStub.tsx.
vi.mock('@monaco-editor/react', () => import('./monacoStub'));
vi.mock('@/lib/monaco', () => ({}));

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetDb();
  resetMockConfig();
  localStorage.clear();
  usePreferences.setState({ theme: null, editorFontSize: 14 });
  setSystemDark(false);
  setViewportWidth(1280);
  document.body.removeAttribute('arco-theme');
  document.title = '';
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

afterAll(() => {
  server.close();
});
