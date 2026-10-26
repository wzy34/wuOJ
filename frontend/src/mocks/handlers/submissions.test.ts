import { describe, expect, it } from 'vitest';
import type {
  SubmissionDetail,
  SubmissionPage,
  SubmissionStatusEvent,
  Verdict,
} from '@/api/generated/model';
import { apiGet, apiPost, apiUrl, signIn } from '@/test/api';
import { db, mockConfig } from '../db';
import { problemFixtures } from '../fixtures/problems';
import { planVerdict, subscribe } from '../mockJudge';

const aPlusB = problemFixtures.find((p) => p.slug === 'a-plus-b')!;
const PROGRAM = 'int main() { return 0; }';

async function submit(sourceCode: string): Promise<SubmissionDetail> {
  const response = await apiPost('/api/submissions', {
    problemId: aPlusB.id,
    language: 'CPP17',
    sourceCode,
  });
  expect(response.status).toBe(201);
  return response.json();
}

function finished(id: number): Promise<void> {
  return new Promise((resolve) => {
    const unsubscribe = subscribe(id, (event) => {
      if (event.status === 'FINISHED') {
        unsubscribe();
        resolve();
      }
    });
  });
}

describe('planVerdict', () => {
  it.each(['AC', 'WA', 'TLE', 'MLE', 'RE', 'CE', 'OLE', 'SE'] as const)(
    'honours the %s marker',
    (verdict) => {
      expect(planVerdict(`${PROGRAM} // @mock-verdict: ${verdict}`, aPlusB).verdict).toBe(verdict);
    },
  );

  it('fails to compile without main and accepts otherwise', () => {
    expect(planVerdict('int helper() {}', aPlusB).verdict).toBe('CE');
    expect(planVerdict(PROGRAM, aPlusB).verdict).toBe('AC');
  });
});

describe('mock judge', () => {
  const expected: Record<Verdict, { tests: number }> = {
    AC: { tests: aPlusB.testCount },
    WA: { tests: 3 },
    TLE: { tests: 3 },
    MLE: { tests: 3 },
    RE: { tests: 3 },
    OLE: { tests: 3 },
    SE: { tests: 1 },
    CE: { tests: 0 },
  };

  it.each(Object.entries(expected))('finishes with %s', async (verdict, { tests }) => {
    mockConfig.rateLimitMax = 100;
    await signIn();
    const created = await submit(`${PROGRAM} // @mock-verdict: ${verdict}`);
    expect(created.status).toBe('PENDING');
    await finished(created.id);

    const detail: SubmissionDetail = await (await apiGet(`/api/submissions/${created.id}`)).json();
    expect(detail).toMatchObject({ status: 'FINISHED', verdict });
    expect(detail.testResults).toHaveLength(tests);
    if (tests > 0) expect(detail.testResults.at(-1)!.verdict).toBe(verdict);
    if (verdict === 'CE') expect(detail.compileOutput).toContain('error');
    if (verdict === 'TLE') expect(detail.timeMs).toBe(aPlusB.timeLimitMs);
  });

  it('emits each status change in order', async () => {
    await signIn();
    const created = await submit(`${PROGRAM} // @mock-verdict: WA`);
    const events: string[] = [];
    await new Promise<void>((resolve) => {
      subscribe(created.id, (event) => {
        events.push(event.status + (event.currentTest ? `#${event.currentTest}` : ''));
        if (event.status === 'FINISHED') resolve();
      });
    });
    expect(events).toEqual(['COMPILING', 'RUNNING#1', 'RUNNING#2', 'RUNNING#3', 'FINISHED']);
  });

  it('streams events over SSE, starting with the current state', async () => {
    await signIn();
    const created = await submit(`${PROGRAM} // @mock-verdict: RE`);
    const received = await new Promise<SubmissionStatusEvent[]>((resolve, reject) => {
      const events: SubmissionStatusEvent[] = [];
      const source = new EventSource(apiUrl(`/api/submissions/${created.id}/events`));
      source.addEventListener('status', (message) => {
        const event = JSON.parse((message as MessageEvent<string>).data) as SubmissionStatusEvent;
        events.push(event);
        if (event.status === 'FINISHED') {
          source.close();
          resolve(events);
        }
      });
      source.onerror = () => {
        source.close();
        reject(new Error('stream failed'));
      };
    });
    expect(received[0].submissionId).toBe(created.id);
    expect(received.at(-1)).toMatchObject({ status: 'FINISHED', verdict: 'RE' });
    expect(received.some((e) => e.status === 'RUNNING' && e.totalTests === aPlusB.testCount)).toBe(
      true,
    );
  });
});

describe('submissions mock', () => {
  it('validates the body and the CSRF header', async () => {
    await signIn();
    const blank = await apiPost('/api/submissions', {
      problemId: aPlusB.id,
      language: 'CPP17',
      sourceCode: '  \n ',
    });
    expect(blank.status).toBe(400);
    expect((await blank.json()).errors[0].field).toBe('sourceCode');

    const huge = await apiPost('/api/submissions', {
      problemId: aPlusB.id,
      language: 'CPP17',
      sourceCode: 'x'.repeat(65_537),
    });
    expect(huge.status).toBe(400);

    const noCsrf = await apiPost(
      '/api/submissions',
      { problemId: aPlusB.id, language: 'CPP17', sourceCode: PROGRAM },
      { csrf: false },
    );
    expect(noCsrf.status).toBe(403);
  });

  it('rate limits rapid submissions with Retry-After', async () => {
    await signIn();
    for (let i = 0; i < mockConfig.rateLimitMax; i++) await submit(PROGRAM);
    const limited = await apiPost('/api/submissions', {
      problemId: aPlusB.id,
      language: 'CPP17',
      sourceCode: PROGRAM,
    });
    expect(limited.status).toBe(429);
    expect(Number(limited.headers.get('Retry-After'))).toBeGreaterThan(0);
  });

  it('lists only my submissions by default, newest first', async () => {
    await signIn();
    const mine: SubmissionPage = await (await apiGet('/api/submissions')).json();
    expect(mine.items.every((s) => s.user.username === 'student')).toBe(true);
    expect(mine.items.map((s) => s.id)).toEqual([5, 2, 1]);
  });

  it('filters all submissions by verdict and problem', async () => {
    await signIn();
    const accepted: SubmissionPage = await (
      await apiGet('/api/submissions?scope=all&verdict=AC')
    ).json();
    expect(accepted.items.map((s) => [s.id, s.user.username])).toEqual([
      [3, 'alice'],
      [2, 'student'],
    ]);

    const forProblem: SubmissionPage = await (
      await apiGet('/api/submissions?scope=all&problem=prime-sieve')
    ).json();
    expect(forProblem.items.map((s) => s.id)).toEqual([4]);
  });

  it('shows source code and compiler output only to the owner', async () => {
    await signIn('bob');
    const own: SubmissionDetail = await (await apiGet('/api/submissions/4')).json();
    expect(own.sourceCode).toBeDefined();
    expect(own.compileOutput).toBeDefined();

    await signIn('student');
    const others: SubmissionDetail = await (await apiGet('/api/submissions/4')).json();
    expect(others.verdict).toBe('CE');
    expect(others).not.toHaveProperty('sourceCode');
    expect(others).not.toHaveProperty('compileOutput');
    expect((await apiGet('/api/submissions/999999')).status).toBe(404);
  });

  it('persists new submissions', async () => {
    await signIn();
    const created = await submit(PROGRAM);
    expect(db.submissions.some((s) => s.id === created.id)).toBe(true);
    expect(localStorage.getItem('wuoj-mock-db-v1')).toContain(`"id":${created.id}`);
  });
});
