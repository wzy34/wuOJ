import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { LoginPage } from '@/features/auth/LoginPage';
import { RequireAuth } from '@/features/auth/RequireAuth';
import { ProblemListPage } from '@/features/problems/ProblemListPage';
import { SubmissionDetailPage } from '@/features/submissions/SubmissionDetailPage';
import { SubmissionListPage } from '@/features/submissions/SubmissionListPage';
import { FullPageSpinner } from '@/components/PageState';
import { AppLayout } from '@/layouts/AppLayout';
import { NotFoundPage } from './NotFoundPage';
import { RootLayout } from './RootLayout';
import { RouteError } from './RouteError';

export const routes: RouteObject[] = [
  {
    Component: RootLayout,
    ErrorBoundary: RouteError,
    // Shown while a lazy route (the workspace) loads on a cold start.
    HydrateFallback: FullPageSpinner,
    children: [
      { path: '/login', Component: LoginPage },
      {
        Component: RequireAuth,
        children: [
          {
            Component: AppLayout,
            children: [
              {
                // Pathless route so a crashing page is replaced below the top bar, not with it.
                ErrorBoundary: RouteError,
                children: [
                  { path: '/', element: <Navigate to="/problems" replace /> },
                  { path: '/problems', Component: ProblemListPage },
                  {
                    path: '/problems/:slug',
                    // Monaco and KaTeX load with this chunk only.
                    lazy: async () => {
                      const { WorkspacePage } = await import('@/features/problems/WorkspacePage');
                      return { Component: WorkspacePage };
                    },
                  },
                  { path: '/submissions', Component: SubmissionListPage },
                  { path: '/submissions/:id', Component: SubmissionDetailPage },
                  { path: '*', Component: NotFoundPage },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(routes);
}
