import type { SubmissionDetail } from '@/api/generated/model';

/** "test 3 of 10" while running, for the verdict tag. */
export function runningProgress(submission: SubmissionDetail): string | undefined {
  if (submission.status !== 'RUNNING' || !submission.currentTest || !submission.totalTests) {
    return undefined;
  }
  return `test ${submission.currentTest} of ${submission.totalTests}`;
}
