import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';

/**
 * Mock mode (`pnpm dev:mock`) answers /api in the browser. The condition is false at build time in
 * production, so the dynamic import and every mock module are left out of the bundle.
 */
async function enableMocking(): Promise<void> {
  if (import.meta.env.DEV && import.meta.env.VITE_API_MOCK === 'true') {
    const { startMockWorker } = await import('./mocks/browser');
    await startMockWorker();
  }
}

void enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
