import type { SubmissionStatusEvent, TestResult, Verdict } from '@/api/generated/model';
import { db, mockConfig, saveDb, type MockSubmission } from './db';
import type { MockProblem } from './fixtures/problems';

/** Put `// @mock-verdict: TLE` (or any verdict) in the code to force that result. */
const VERDICT_MARKER = /@mock-verdict:\s*(AC|WA|TLE|MLE|RE|CE|OLE|SE)\b/;

type Plan = { verdict: Verdict; failAt?: number; compileOutput?: string };

export function planVerdict(sourceCode: string, problem: MockProblem): Plan {
  const forced = VERDICT_MARKER.exec(sourceCode)?.[1] as Verdict | undefined;
  if (forced === 'CE') {
    return {
      verdict: 'CE',
      compileOutput:
        "main.cpp: In function 'int main()':\nmain.cpp:1:1: error: compilation failed as requested by @mock-verdict",
    };
  }
  if (!forced && !/\bmain\s*\(/.test(sourceCode)) {
    return {
      verdict: 'CE',
      compileOutput:
        "/usr/bin/ld: in function `_start':\n(.text+0x1b): undefined reference to `main'\ncollect2: error: ld returned 1 exit status",
    };
  }
  const verdict = forced ?? 'AC';
  if (verdict === 'AC') return { verdict };
  if (verdict === 'SE') return { verdict, failAt: 1 };
  return { verdict, failAt: Math.min(3, problem.testCount) };
}

export function toStatusEvent(submission: MockSubmission): SubmissionStatusEvent {
  return {
    submissionId: submission.id,
    status: submission.status,
    currentTest: submission.currentTest,
    totalTests: submission.totalTests,
    verdict: submission.verdict,
    timeMs: submission.timeMs,
    memoryKb: submission.memoryKb,
  };
}

type Listener = (event: SubmissionStatusEvent) => void;
const listeners = new Map<number, Set<Listener>>();

export function subscribe(submissionId: number, listener: Listener): () => void {
  const set = listeners.get(submissionId) ?? new Set<Listener>();
  set.add(listener);
  listeners.set(submissionId, set);
  return () => set.delete(listener);
}

function update(submission: MockSubmission): void {
  saveDb();
  const event = toStatusEvent(submission);
  listeners.get(submission.id)?.forEach((listener) => listener(event));
}

function testResult(
  submission: MockSubmission,
  problem: MockProblem,
  testNumber: number,
  verdict: Verdict,
): TestResult {
  let timeMs = 2 + ((submission.id * 7 + testNumber * 13) % 60);
  let memoryKb = 3200 + ((submission.id * 31 + testNumber * 17) % 1800);
  if (verdict === 'TLE') timeMs = problem.timeLimitMs;
  if (verdict === 'MLE') memoryKb = problem.memoryLimitMb * 1024;
  if (verdict === 'SE') timeMs = 0;
  return { testNumber, verdict, timeMs, memoryKb };
}

function finish(submission: MockSubmission, verdict: Verdict, compileOutput?: string): void {
  submission.status = 'FINISHED';
  submission.verdict = verdict;
  submission.currentTest = undefined;
  if (compileOutput) submission.compileOutput = compileOutput;
  if (submission.testResults.length > 0) {
    submission.timeMs = Math.max(...submission.testResults.map((r) => r.timeMs));
    submission.memoryKb = Math.max(...submission.testResults.map((r) => r.memoryKb));
  }
  update(submission);
}

const wait = () => new Promise((resolve) => setTimeout(resolve, mockConfig.judgeStepMs));

/** Walks a submission through PENDING → COMPILING → RUNNING (test by test) → FINISHED. */
export async function judge(submission: MockSubmission, problem: MockProblem): Promise<void> {
  const plan = planVerdict(submission.sourceCode, problem);
  // Restart from scratch, e.g. after a page reload interrupted an earlier run.
  Object.assign(submission, {
    status: 'PENDING',
    verdict: undefined,
    timeMs: undefined,
    memoryKb: undefined,
    currentTest: undefined,
    totalTests: undefined,
    compileOutput: undefined,
    testResults: [],
  });
  update(submission);

  await wait();
  submission.status = 'COMPILING';
  update(submission);

  await wait();
  if (plan.verdict === 'CE') {
    finish(submission, 'CE', plan.compileOutput);
    return;
  }

  submission.status = 'RUNNING';
  submission.totalTests = problem.testCount;
  for (let testNumber = 1; testNumber <= problem.testCount; testNumber++) {
    submission.currentTest = testNumber;
    update(submission);
    await wait();
    const failed = testNumber === plan.failAt;
    submission.testResults.push(
      testResult(submission, problem, testNumber, failed ? plan.verdict : 'AC'),
    );
    if (failed) {
      finish(submission, plan.verdict);
      return;
    }
  }
  finish(submission, 'AC');
}

/** Resumes judging of submissions that a page reload left unfinished. */
export function resumeUnfinished(problems: MockProblem[]): void {
  for (const submission of db.submissions) {
    if (submission.status === 'FINISHED') continue;
    const problem = problems.find((p) => p.id === submission.problem.id);
    if (problem) void judge(submission, problem);
  }
}
