import { rmSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import type { Plugin } from 'vite';
import { defineConfig } from 'vitest/config';

/** Mock mode is dev-only, so the MSW worker that `public/` holds for it is dropped from builds. */
function excludeMockServiceWorker(): Plugin {
  let outDir = 'dist';
  return {
    name: 'wuoj:exclude-mock-service-worker',
    apply: 'build',
    configResolved(config) {
      outDir = join(config.root, config.build.outDir);
    },
    closeBundle() {
      rmSync(join(outDir, 'mockServiceWorker.js'), { force: true });
    },
  };
}

export default defineConfig({
  plugins: [react(), excludeMockServiceWorker()],
  resolve: {
    alias: [
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      {
        // monaco-editor's main entry registers the TS/CSS/HTML/JSON language services, whose
        // workers add ~9 MB to the build. The editor only edits C++, so they become a no-op.
        find: /^\.{1,2}\/languages\/features\/(typescript|css|html|json)\/register\.js$/,
        replacement: fileURLToPath(
          new URL('./src/lib/monacoNoLanguageService.ts', import.meta.url),
        ),
      },
    ],
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      // The Spring Boot API; also carries the SSE stream for submission events.
      '/api': {
        target: 'http://localhost:8080',
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/polyfills.ts', './src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
});
