import { describe, expect, it } from 'vitest';
import type {
  AdminProblem,
  AdminProblemPage,
  AdminTestSaveResult,
  AdminTestSet,
  ApiProblem,
  AuditEntryPage,
  ProblemContent,
  ProblemPage,
} from '@/api/generated/model';
import { apiForm, apiGet, apiPut, signIn } from '@/test/api';
import { problems, resetDb } from '../db';
import { seedProblems } from '../fixtures/problems';

const aPlusB = () => problems.find((p) => p.slug === 'a-plus-b')!;

const content = (overrides: Partial<ProblemContent> = {}): ProblemContent => ({
  displayId: '2001',
  slug: 'new-problem',
  title: 'New problem',
  difficulty: 'EASY',
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  statement: 'Print $1$.',
  visible: true,
  samples: [{ input: '\n', output: '1\n' }],
  ...overrides,
});

async function read(path: string, init?: RequestInit): Promise<string> {
  const response = await fetch(new URL(path, window.location.origin), init);
  return response.text();
}

describe('mock problem data', () => {
  it('seeds tests that a reset restores', () => {
    aPlusB().tests = [];
    aPlusB().title = 'Changed';
    resetDb();
    expect(aPlusB().title).toBe('A + B');
    expect(aPlusB().tests).toHaveLength(5);
    expect(aPlusB().testdataVersion).toBe(
      seedProblems().find((p) => p.slug === 'a-plus-b')!.testdataVersion,
    );
  });

  it('hides unpublished problems from everyone but admins', async () => {
    expect((await apiGet('/api/problems/shortest-path-draft')).status).toBe(404);
    await signIn('student');
    const list: ProblemPage = await (await apiGet('/api/problems?q=draft')).json();
    expect(list.totalItems).toBe(0);
    await signIn('admin');
    expect((await apiGet('/api/problems/shortest-path-draft')).status).toBe(200);
  });
});

describe('admin problems mock', () => {
  it('is for admins only', async () => {
    expect((await apiGet('/api/admin/problems')).status).toBe(401);
    await signIn('alice');
    expect((await apiGet('/api/admin/problems')).status).toBe(403);
  });

  it('lists hidden problems too, with filters', async () => {
    await signIn('admin');
    const hidden: AdminProblemPage = await (
      await apiGet('/api/admin/problems?visible=false')
    ).json();
    expect(hidden.items.map((p) => p.slug)).toEqual(['shortest-path-draft']);
    expect(hidden.items[0]).toMatchObject({ visible: false, testCount: 3 });
    const search: AdminProblemPage = await (
      await apiGet(`/api/admin/problems?q=${encodeURIComponent('A + B')}`)
    ).json();
    expect(search.items.map((p) => p.slug)).toEqual(['a-plus-b']);
  });

  it('creates hidden problems with uploaded tests', async () => {
    await signIn('admin');
    const response = await apiForm(
      '/api/admin/problems',
      {
        problem: content(),
        manifest: { tests: [{ input: { upload: 0 }, output: { upload: 1 } }] },
      },
      ['in', 'out'],
    );
    expect(response.status).toBe(201);
    const created: AdminProblem = await response.json();
    expect(response.headers.get('Location')).toBe(`/api/admin/problems/${created.id}`);
    expect(created).toMatchObject({ testCount: 1, revision: 1, content: { visible: false } });
    expect((await apiGet('/api/problems/new-problem')).status).toBe(200);

    const audit: AuditEntryPage = await (await apiGet('/api/admin/audit')).json();
    expect(audit.items[0]).toMatchObject({
      action: 'PROBLEM_CREATED',
      problem: { slug: 'new-problem' },
    });
    expect(audit.items[0].target).toBeUndefined();
  });

  it('reports every invalid field', async () => {
    await signIn('admin');
    const response = await apiForm('/api/admin/problems', {
      problem: content({ title: '', timeLimitMs: 50, slug: 'a-plus-b' }),
      manifest: { tests: [] },
    });
    expect(response.status).toBe(400);
    const body: ApiProblem = await response.json();
    expect(body.errors?.map((e) => e.field)).toEqual(['title', 'timeLimitMs', 'slug']);
  });

  it('updates content, refusing stale revisions', async () => {
    await signIn('admin');
    const problem: AdminProblem = await (await apiGet(`/api/admin/problems/${aPlusB().id}`)).json();
    const edited = { ...problem.content, title: 'A plus B' };

    const first = await apiPut(`/api/admin/problems/${problem.id}`, {
      revision: 1,
      content: edited,
    });
    expect(((await first.json()) as AdminProblem).revision).toBe(2);
    const page: ProblemPage = await (await apiGet('/api/problems?q=plus')).json();
    expect(page.items.map((p) => p.title)).toEqual(['A plus B']);

    const stale = await apiPut(`/api/admin/problems/${problem.id}`, {
      revision: 1,
      content: problem.content,
    });
    expect(stale.status).toBe(409);

    const unchanged = await apiPut(`/api/admin/problems/${problem.id}`, {
      revision: 2,
      content: edited,
    });
    expect(((await unchanged.json()) as AdminProblem).revision).toBe(2);
  });

  it('serves test files whole and in ranges', async () => {
    await signIn('admin');
    const set: AdminTestSet = await (
      await apiGet(`/api/admin/problems/${aPlusB().id}/tests`)
    ).json();
    expect(set.tests).toHaveLength(5);
    expect(set.tests[1]).toEqual({ number: 2, inputBytes: 2, outputBytes: 11 });
    expect(await read(`/api/admin/problems/${aPlusB().id}/tests/2/output`)).toBe('a-plus-b 2\n');
    const part = await fetch(
      new URL(`/api/admin/problems/${aPlusB().id}/tests/2/output`, location.origin),
      {
        headers: { Range: 'bytes=0-7' },
      },
    );
    expect(part.status).toBe(206);
    expect(part.headers.get('Content-Range')).toBe('bytes 0-7/11');
    expect(await part.text()).toBe('a-plus-b');
  });

  it('saves tests: keep, upload, reorder and delete', async () => {
    await signIn('admin');
    const base = aPlusB().testdataVersion;
    const response = await apiForm(
      `/api/admin/problems/${aPlusB().id}/tests`,
      {
        manifest: {
          baseVersion: base,
          tests: [
            { input: { keep: 3 }, output: { keep: 3 } },
            { input: { keep: 1 }, output: { upload: 0 } },
          ],
        },
      },
      ['fixed\n'],
    );
    const result: AdminTestSaveResult = await response.json();
    expect(result.changed).toBe(true);
    expect(result.testSet.tests).toHaveLength(2);
    expect(await read(`/api/admin/problems/${aPlusB().id}/tests/1/input`)).toBe('3\n');
    expect(await read(`/api/admin/problems/${aPlusB().id}/tests/2/output`)).toBe('fixed\n');

    const audit: AuditEntryPage = await (await apiGet('/api/admin/audit')).json();
    expect(audit.items[0]).toMatchObject({
      action: 'TESTS_UPDATED',
      fromTestCount: 5,
      toTestCount: 2,
    });

    const stale = await apiForm(`/api/admin/problems/${aPlusB().id}/tests`, {
      manifest: { baseVersion: base, tests: [{ input: { keep: 1 }, output: { keep: 1 } }] },
    });
    expect(stale.status).toBe(409);
  });

  it('reports unchanged tests and bad references', async () => {
    await signIn('admin');
    const base = aPlusB().testdataVersion;
    const keepAll = [1, 2, 3, 4, 5].map((n) => ({ input: { keep: n }, output: { keep: n } }));
    const same = await apiForm(`/api/admin/problems/${aPlusB().id}/tests`, {
      manifest: { baseVersion: base, tests: keepAll },
    });
    expect(((await same.json()) as AdminTestSaveResult).changed).toBe(false);

    const dangling = await apiForm(
      `/api/admin/problems/${aPlusB().id}/tests`,
      { manifest: { baseVersion: base, tests: [{ input: { keep: 9 }, output: { upload: 0 } }] } },
      ['x', 'unused'],
    );
    expect(dangling.status).toBe(400);
    const body: ApiProblem = await dangling.json();
    expect(body.errors?.map((e) => e.message)).toEqual([
      'tests[0].input: keep 9 is not a test of the base version',
      'files[1] must be used exactly once, but is used 0 times',
    ]);
  });
});
