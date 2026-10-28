import { http, HttpResponse } from 'msw';
import type { Problem, ProblemSummary, ProblemUserStatus } from '@/api/generated/model';
import { db, problems, type MockUser } from '../db';
import type { MockProblem } from '../fixtures/problems';
import { latency, oneOf, paginate, parsePaging, problemResponse, viewer } from './util';

/** Visitors have no status. */
export function problemStatus(user: MockUser | undefined, problemId: number): ProblemUserStatus {
  if (!user) return 'NONE';
  const mine = db.submissions.filter((s) => s.user.id === user.id && s.problem.id === problemId);
  if (mine.some((s) => s.verdict === 'AC')) return 'SOLVED';
  return mine.length > 0 ? 'ATTEMPTED' : 'NONE';
}

function toProblem(problem: MockProblem, user: MockUser | undefined): Problem {
  const { testCount: _testCount, ...rest } = problem;
  return { ...rest, status: problemStatus(user, problem.id) };
}

export const problemHandlers = [
  http.get('/api/problems', async ({ request }) => {
    await latency();
    const user = viewer();
    if (user instanceof Response) return user;

    const url = new URL(request.url);
    const paging = parsePaging(url);
    if (paging instanceof Response) return paging;

    const q = url.searchParams.get('q')?.trim() ?? '';
    if (q.length > 100) {
      return problemResponse(400, 'Bad Request', 'Search text is too long.', {
        errors: [{ field: 'q', message: 'must be at most 100 characters' }],
      });
    }
    const difficultyParam = url.searchParams.get('difficulty');
    const difficulty = oneOf(difficultyParam, ['EASY', 'MEDIUM', 'HARD'] as const);
    if (difficultyParam && !difficulty) {
      return problemResponse(400, 'Bad Request', 'Unknown difficulty.', {
        errors: [{ field: 'difficulty', message: 'must be EASY, MEDIUM or HARD' }],
      });
    }

    const matches: ProblemSummary[] = problems
      .filter((p) => !q || p.title.toLowerCase().includes(q.toLowerCase()))
      .filter((p) => !difficulty || p.difficulty === difficulty)
      .map((p) => ({
        id: p.id,
        displayId: p.displayId,
        slug: p.slug,
        title: p.title,
        difficulty: p.difficulty,
        status: problemStatus(user, p.id),
      }));
    return HttpResponse.json(paginate(matches, paging.page, paging.size));
  }),

  http.get('/api/problems/:slug', async ({ params }) => {
    await latency();
    const user = viewer();
    if (user instanceof Response) return user;

    const problem = problems.find((p) => p.slug === params.slug);
    if (!problem) return problemResponse(404, 'Not Found', 'Problem not found.');
    return HttpResponse.json(toProblem(problem, user));
  }),
];
