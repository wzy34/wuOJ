import { describe, expect, it } from 'vitest';
import type { Problem, ProblemPage } from '@/api/generated/model';
import { apiGet, signIn } from '@/test/api';
import { problemFixtures } from '../fixtures/problems';

describe('problem fixtures', () => {
  const featured = problemFixtures.filter((p) => !p.slug.startsWith('warm-up-'));

  it('has at least five featured problems of mixed difficulty', () => {
    expect(featured.length).toBeGreaterThanOrEqual(5);
    expect(new Set(featured.map((p) => p.difficulty))).toEqual(new Set(['EASY', 'MEDIUM', 'HARD']));
  });

  it('gives every featured problem math and samples', () => {
    for (const problem of featured) {
      expect(problem.statement).toMatch(/\$[^$]+\$/);
      expect(problem.samples.length).toBeGreaterThan(0);
    }
  });
});

describe('problems mock', () => {
  it('answers visitors without a status', async () => {
    const page: ProblemPage = await (await apiGet('/api/problems?size=3')).json();
    expect(page.items.map((p) => p.status)).toEqual(['NONE', 'NONE', 'NONE']);
    const problem: Problem = await (await apiGet('/api/problems/a-plus-b')).json();
    expect(problem.status).toBe('NONE');
  });

  it('pages through the list', async () => {
    await signIn();
    const first: ProblemPage = await (await apiGet('/api/problems')).json();
    expect(first).toMatchObject({ page: 1, size: 20, totalItems: 45, totalPages: 3 });
    expect(first.items).toHaveLength(20);

    const third: ProblemPage = await (await apiGet('/api/problems?page=3&size=20')).json();
    expect(third.items).toHaveLength(5);
  });

  it('rejects oversized pages', async () => {
    await signIn();
    expect((await apiGet('/api/problems?size=500')).status).toBe(400);
  });

  it('searches titles case-insensitively and filters by difficulty', async () => {
    await signIn();
    const graph: ProblemPage = await (await apiGet('/api/problems?q=GRAPH')).json();
    expect(graph.items.map((p) => p.title)).toEqual(['Bipartite Graph Check']);

    const hard: ProblemPage = await (await apiGet('/api/problems?difficulty=HARD&size=100')).json();
    expect(hard.items.length).toBeGreaterThan(0);
    expect(hard.items.every((p) => p.difficulty === 'HARD')).toBe(true);
  });

  it("reports the student's solved status", async () => {
    await signIn();
    const page: ProblemPage = await (await apiGet('/api/problems?size=3')).json();
    expect(page.items.map((p) => [p.slug, p.status])).toEqual([
      ['a-plus-b', 'SOLVED'],
      ['maximum-subarray-sum', 'ATTEMPTED'],
      ['prime-sieve', 'NONE'],
    ]);
  });

  it('returns a problem by slug without hidden test data', async () => {
    await signIn();
    const problem: Problem = await (await apiGet('/api/problems/a-plus-b')).json();
    expect(problem).toMatchObject({ title: 'A + B', timeLimitMs: 1000, memoryLimitMb: 256 });
    expect(problem).not.toHaveProperty('testCount');
    expect((await apiGet('/api/problems/no-such-problem')).status).toBe(404);
  });
});
