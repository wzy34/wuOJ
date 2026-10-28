import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { RequireAdmin, RequireAuth } from '@/features/auth/RequireAuth';
import { SessionGate } from '@/features/auth/SessionGate';
import { SettingsPage } from '@/features/auth/SettingsPage';
import { AdminAuditPage } from '@/features/admin/AdminAuditPage';
import { AdminUsersPage } from '@/features/admin/AdminUsersPage';
import { ProblemListPage } from '@/features/problems/ProblemListPage';
import { SubmissionDetailPage } from '@/features/submissions/SubmissionDetailPage';
import { SubmissionListPage } from '@/features/submissions/SubmissionListPage';
import { ProfilePage } from '@/features/users/ProfilePage';
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
      { path: '/register', Component: RegisterPage },
      {
        Component: SessionGate,
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
                  { path: '/users/:username', Component: ProfilePage },
                  {
                    Component: RequireAuth,
                    children: [{ path: '/settings', Component: SettingsPage }],
                  },
                  {
                    Component: RequireAdmin,
                    children: [
                      { path: '/admin', element: <Navigate to="/admin/users" replace /> },
                      { path: '/admin/users', Component: AdminUsersPage },
                      { path: '/admin/audit', Component: AdminAuditPage },
                    ],
                  },
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
