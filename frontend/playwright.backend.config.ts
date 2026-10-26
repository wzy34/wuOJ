import { defineConfig, devices } from '@playwright/test';

const PORT = 5181;

/**
 * The smoke test against the real backend instead of the mocks. Start the backend first, with the development data
 * imported and the simulated judge (which understands the `// @mock-verdict:` markers):
 *
 *   cd backend && ./gradlew importDevData && ./gradlew bootRun --args='--oj.judge.simulated=true'
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
  // Plain dev mode: /api is proxied to the backend on :8080.
  webServer: {
    command: `pnpm exec vite --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: false,
    timeout: 60_000,
  },
});
