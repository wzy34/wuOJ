import { getAuthMock } from '@/api/generated/auth/auth.msw';
import { getProblemsMock } from '@/api/generated/problems/problems.msw';
import { getSubmissionsMock } from '@/api/generated/submissions/submissions.msw';
import { authHandlers } from './handlers/auth';
import { problemHandlers } from './handlers/problems';
import { submissionEventHandlers, submissionHandlers } from './handlers/submissions';

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
    ...getAuthMock(),
    ...getProblemsMock(),
    ...getSubmissionsMock(),
  ];
}
