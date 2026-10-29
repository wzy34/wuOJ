import { getAccountMock } from '@/api/generated/account/account.msw';
import { getAdminMock } from '@/api/generated/admin/admin.msw';
import { getAuthMock } from '@/api/generated/auth/auth.msw';
import { getProblemsMock } from '@/api/generated/problems/problems.msw';
import { getSubmissionsMock } from '@/api/generated/submissions/submissions.msw';
import { getUsersMock } from '@/api/generated/users/users.msw';
import { adminProblemHandlers } from './handlers/adminProblems';
import { authHandlers } from './handlers/auth';
import { problemHandlers } from './handlers/problems';
import { submissionEventHandlers, submissionHandlers } from './handlers/submissions';
import { userHandlers } from './handlers/users';

/**
 * Hand-written stateful handlers come first and win; the orval-generated faker handlers stay as a
 * fallback for any contract endpoint without a hand-written mock.
 */
export function createHandlers() {
  return [
    ...authHandlers,
    ...problemHandlers,
    ...submissionHandlers,
    ...submissionEventHandlers(),
    ...userHandlers,
    ...adminProblemHandlers,
    ...getAuthMock(),
    ...getAccountMock(),
    ...getProblemsMock(),
    ...getSubmissionsMock(),
    ...getUsersMock(),
    ...getAdminMock(),
  ];
}
