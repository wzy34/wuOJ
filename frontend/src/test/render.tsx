import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { AppProviders } from '@/app/AppProviders';
import { routes } from '@/app/router';
import { createQueryClient } from '@/lib/queryClient';

/** Renders the real route tree at `path`, with the mock API behind it. */
export function renderRoute(path: string) {
  const notify = vi.fn();
  const queryClient = createQueryClient({ notify, retryDelay: 0 });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const view = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...view, router, queryClient, notify };
}
