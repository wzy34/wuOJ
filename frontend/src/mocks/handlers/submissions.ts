import { http, HttpResponse, sse } from 'msw';
import type {
  FieldError,
  SubmissionDetail,
  SubmissionStatusEvent,
  SubmissionSummary,
} from '@/api/generated/model';
import {
  db,
  getSessionUser,
  mockConfig,
  problems,
  saveDb,
  type MockSubmission,
  type MockUser,
} from '../db';
import { judge, subscribe, toStatusEvent } from '../mockJudge';
import {
  checkCsrf,
  latency,
  oneOf,
  paginate,
  parsePaging,
  problemResponse,
  requireUser,
} from './util';

const VERDICTS = ['AC', 'WA', 'TLE', 'MLE', 'RE', 'CE', 'OLE', 'SE'] as const;
const MAX_SOURCE_LENGTH = 65_536;

function toSummary(s: MockSubmission): SubmissionSummary {
  return {
    id: s.id,
    user: s.user,
    problem: s.problem,
    language: s.language,
    status: s.status,
    verdict: s.verdict,
    timeMs: s.timeMs,
    memoryKb: s.memoryKb,
    createdAt: s.createdAt,
  };
}

/** Source code and compiler output are only for the owner. */
function toDetail(s: MockSubmission, viewer: MockUser): SubmissionDetail {
  const { sourceCode, compileOutput, ...rest } = s;
  const detail: SubmissionDetail = { ...rest, testResults: [...s.testResults] };
  if (s.user.id === viewer.id) {
    detail.sourceCode = sourceCode;
    if (compileOutput) detail.compileOutput = compileOutput;
  }
  return detail;
}

/** Seconds until the user may submit again, or 0 when within the limit. */
function retryAfterSeconds(userId: number, now: number): number {
  const windowStart = now - mockConfig.rateLimitWindowMs;
  const recent = (db.recentSubmits.get(userId) ?? []).filter((t) => t > windowStart);
  db.recentSubmits.set(userId, recent);
  if (recent.length < mockConfig.rateLimitMax) return 0;
  return Math.ceil((recent[0] - windowStart) / 1000);
}

function validate(body: Record<string, unknown>): FieldError[] {
  const errors: FieldError[] = [];
  if (typeof body.problemId !== 'number') {
    errors.push({ field: 'problemId', message: 'must be a number' });
  }
  if (body.language !== 'CPP17') errors.push({ field: 'language', message: 'must be CPP17' });
  if (typeof body.sourceCode !== 'string' || body.sourceCode.trim() === '') {
    errors.push({ field: 'sourceCode', message: 'must not be blank' });
  } else if (body.sourceCode.length > MAX_SOURCE_LENGTH) {
    errors.push({ field: 'sourceCode', message: 'must be at most 64 KB' });
  }
  return errors;
}

export const submissionHandlers = [
  http.get('/api/submissions', async ({ request }) => {
    await latency();
    const user = requireUser();
    if (user instanceof Response) return user;

    const url = new URL(request.url);
    const paging = parsePaging(url);
    if (paging instanceof Response) return paging;

    const scopeParam = url.searchParams.get('scope');
    const scope = oneOf(scopeParam ?? 'mine', ['mine', 'all'] as const);
    const verdictParam = url.searchParams.get('verdict');
    const verdict = oneOf(verdictParam, VERDICTS);
    if (!scope || (verdictParam && !verdict)) {
      return problemResponse(400, 'Bad Request', 'Invalid filter.');
    }
    const problemSlug = url.searchParams.get('problem');

    const matches = db.submissions
      .filter((s) => scope === 'all' || s.user.id === user.id)
      .filter((s) => !problemSlug || s.problem.slug === problemSlug)
      .filter((s) => !verdict || s.verdict === verdict)
      .sort((a, b) => b.id - a.id)
      .map(toSummary);
    return HttpResponse.json(paginate(matches, paging.page, paging.size));
  }),

  http.post('/api/submissions', async ({ request }) => {
    await latency();
    const user = requireUser();
    if (user instanceof Response) return user;
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;

    const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
    const errors = validate(body);
    if (errors.length > 0) {
      return problemResponse(400, 'Bad Request', 'Invalid submission.', { errors });
    }
    const problem = problems.find((p) => p.id === body.problemId);
    if (!problem) return problemResponse(404, 'Not Found', 'Problem not found.');

    const now = Date.now();
    const wait = retryAfterSeconds(user.id, now);
    if (wait > 0) {
      return problemResponse(429, 'Too Many Requests', `Try again in ${wait} seconds.`, {
        headers: { 'Retry-After': String(wait) },
      });
    }
    db.recentSubmits.get(user.id)!.push(now);

    const submission: MockSubmission = {
      id: db.nextSubmissionId++,
      user: { id: user.id, username: user.username },
      problem: {
        id: problem.id,
        displayId: problem.displayId,
        slug: problem.slug,
        title: problem.title,
      },
      language: 'CPP17',
      status: 'PENDING',
      createdAt: new Date(now).toISOString(),
      testResults: [],
      sourceCode: body.sourceCode as string,
    };
    db.submissions.push(submission);
    saveDb();
    void judge(submission, problem);

    return HttpResponse.json(toDetail(submission, user), {
      status: 201,
      headers: { Location: `/api/submissions/${submission.id}` },
    });
  }),

  http.get('/api/submissions/:id', async ({ params }) => {
    await latency();
    const user = requireUser();
    if (user instanceof Response) return user;

    const submission = db.submissions.find((s) => s.id === Number(params.id));
    if (!submission) return problemResponse(404, 'Not Found', 'Submission not found.');
    return HttpResponse.json(toDetail(submission, user));
  }),
];

/**
 * The live event stream. `sse()` needs a global EventSource when the handler is created, which
 * browsers have and the test setup polyfills.
 */
export function submissionEventHandlers() {
  return [
    sse<{ status: SubmissionStatusEvent }>(
      '/api/submissions/:id/events',
      ({ params, client, request }) => {
        const submission = db.submissions.find((s) => s.id === Number(params.id));
        if (!getSessionUser() || !submission) {
          client.error();
          return;
        }
        const send = (event: SubmissionStatusEvent) => {
          client.send({ event: 'status', data: event });
          if (event.status === 'FINISHED') {
            unsubscribe();
            client.close();
          }
        };
        const unsubscribe = subscribe(submission.id, send);
        request.signal.addEventListener('abort', unsubscribe);
        send(toStatusEvent(submission));
      },
    ),
  ];
}
