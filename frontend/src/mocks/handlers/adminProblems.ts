import { http, HttpResponse } from 'msw';
import type {
  AdminProblem,
  AdminProblemSummary,
  AdminTestSaveResult,
  AdminTestSet,
  FieldError,
  ProblemContent,
  TestFileRef,
  TestManifest,
} from '@/api/generated/model';
import { db, problems, type MockUser } from '../db';
import { testdataVersion, type MockProblem, type MockTest } from '../fixtures/problems';
import { checkCsrf, latency, paginate, parsePaging, problemResponse, requireAdmin } from './util';

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const DISPLAY_ID = /^[A-Za-z0-9_-]{1,32}$/;
const MAX_TESTS = 1000;
const MAX_FILE_BYTES = 64 * 1024 * 1024;

const isString = (value: unknown): value is string => typeof value === 'string';
const isInt = (value: unknown): value is number => Number.isInteger(value);

/** The same rules as the server, reported per field in the same order. */
function validateContent(content: Partial<ProblemContent>, self?: MockProblem): FieldError[] {
  const errors: FieldError[] = [];
  const add = (field: string, message: string) => errors.push({ field, message });
  const { displayId, slug, title, difficulty, timeLimitMs, memoryLimitMb, statement } = content;

  if (!isString(displayId) || !DISPLAY_ID.test(displayId)) {
    add('displayId', "must be 1-32 letters, digits, '-' or '_'");
  }
  if (!isString(slug) || !SLUG.test(slug)) {
    add('slug', 'must be lowercase letters and digits separated by single hyphens');
  } else if (slug.length > 64) {
    add('slug', 'must be at most 64 characters');
  }
  if (!isString(title) || !title.trim()) add('title', 'is required');
  else if (title.length > 200) add('title', 'must be at most 200 characters');
  if (!['EASY', 'MEDIUM', 'HARD'].includes(difficulty as string)) add('difficulty', 'is required');
  if (!isInt(timeLimitMs) || timeLimitMs < 100 || timeLimitMs > 10_000) {
    add('timeLimitMs', 'must be between 100 and 10000');
  }
  if (!isInt(memoryLimitMb) || memoryLimitMb < 16 || memoryLimitMb > 1024) {
    add('memoryLimitMb', 'must be between 16 and 1024');
  }
  if (!isString(statement) || !statement.trim()) add('statement', 'is required');
  if (typeof content.visible !== 'boolean') add('visible', 'is required');
  if (!Array.isArray(content.samples)) {
    add('samples', 'is required');
  } else {
    content.samples.forEach((sample, i) => {
      if (!isString(sample?.input) || !isString(sample?.output)) {
        add('samples', `sample ${i + 1} needs an input and an output`);
      }
    });
  }

  if (!errors.some((e) => e.field === 'displayId')) {
    const owner = problems.find((p) => p.displayId === displayId && p !== self);
    if (owner) add('displayId', `is already used by problem '${owner.slug}'`);
  }
  if (!errors.some((e) => e.field === 'slug')) {
    if (problems.some((p) => p.slug === slug && p !== self)) {
      add('slug', 'is already used by another problem');
    }
  }
  return errors;
}

function invalid(errors: FieldError[]) {
  return problemResponse(400, 'Bad Request', 'The request is invalid.', { errors });
}

function toContent(problem: MockProblem): ProblemContent {
  return {
    displayId: problem.displayId,
    slug: problem.slug,
    title: problem.title,
    difficulty: problem.difficulty,
    timeLimitMs: problem.timeLimitMs,
    memoryLimitMb: problem.memoryLimitMb,
    statement: problem.statement,
    visible: problem.visible,
    samples: problem.samples.map((s) => ({ ...s })),
  };
}

/** Only the content fields of a validated request, with copied samples. */
function contentFields(content: ProblemContent): ProblemContent {
  return {
    displayId: content.displayId,
    slug: content.slug,
    title: content.title,
    difficulty: content.difficulty,
    timeLimitMs: content.timeLimitMs,
    memoryLimitMb: content.memoryLimitMb,
    statement: content.statement,
    visible: content.visible,
    samples: content.samples.map((s) => ({ input: s.input, output: s.output })),
  };
}

function toAdminProblem(problem: MockProblem): AdminProblem {
  return {
    id: problem.id,
    revision: problem.revision,
    testdataVersion: problem.testdataVersion,
    testCount: problem.testCount,
    updatedAt: problem.updatedAt,
    content: toContent(problem),
  };
}

function toTestSet(problem: MockProblem): AdminTestSet {
  return {
    version: problem.testdataVersion,
    tests: problem.tests.map((t, i) => ({
      number: i + 1,
      inputBytes: t.input.byteLength,
      outputBytes: t.output.byteLength,
    })),
  };
}

function find(id: unknown): MockProblem | Response {
  const problem = problems.find((p) => p.id === Number(id));
  return problem ?? problemResponse(404, 'Not Found', 'Problem not found.');
}

function audit(admin: MockUser, problem: MockProblem, entry: Partial<(typeof db.audit)[number]>) {
  db.audit.push({
    id: db.audit.length + 1,
    createdAt: new Date().toISOString(),
    actorId: admin.id,
    problemId: problem.id,
    action: 'PROBLEM_UPDATED',
    ...entry,
  });
}

function parseJson<T>(form: FormData, name: string): T | Response {
  const value = form.get(name);
  try {
    if (typeof value !== 'string') throw new Error('missing');
    return JSON.parse(value) as T;
  } catch {
    return invalid([{ field: name, message: 'is not valid JSON for this request' }]);
  }
}

/**
 * The tests a manifest describes, numbered in order, or the response refusing it: the same checks
 * as the server's assembler.
 */
async function assemble(
  manifest: TestManifest,
  base: MockProblem | undefined,
  files: File[],
): Promise<MockTest[] | Response> {
  const entries = manifest.tests;
  if (!Array.isArray(entries) || entries.length === 0) {
    return invalid([{ field: 'manifest', message: 'must contain at least one test' }]);
  }
  if (entries.length > MAX_TESTS) {
    return problemResponse(413, 'Content Too Large', 'A problem can have at most 1000 tests.');
  }
  if (files.some((f) => f.size > MAX_FILE_BYTES)) {
    return problemResponse(413, 'Content Too Large', 'Each test file can be at most 64 MB.');
  }
  const uploads = await Promise.all(files.map(async (f) => new Uint8Array(await f.arrayBuffer())));
  const uses = uploads.map(() => 0);
  const errors: string[] = [];
  const resolve = (ref: TestFileRef | undefined, label: string, side: keyof MockTest) => {
    const keep = ref?.keep;
    const upload = ref?.upload;
    if (!ref || (keep === undefined) === (upload === undefined)) {
      errors.push(`${label} must give exactly one of keep and upload`);
      return undefined;
    }
    if (keep !== undefined) {
      if (!base) {
        errors.push(`${label}: keep is only allowed when saving the tests of an existing problem`);
        return undefined;
      }
      if (keep < 1 || keep > base.tests.length) {
        errors.push(`${label}: keep ${keep} is not a test of the base version`);
        return undefined;
      }
      return base.tests[keep - 1][side];
    }
    if (upload! < 0 || upload! >= uploads.length) {
      errors.push(`${label}: upload ${upload} is not one of the ${uploads.length} uploaded files`);
      return undefined;
    }
    uses[upload!]++;
    return uploads[upload!];
  };
  const tests: MockTest[] = [];
  entries.forEach((entry, i) => {
    const input = resolve(entry?.input, `tests[${i}].input`, 'input');
    const output = resolve(entry?.output, `tests[${i}].output`, 'output');
    if (input && output) tests.push({ input, output });
  });
  uses.forEach((count, i) => {
    if (count !== 1)
      errors.push(`files[${i}] must be used exactly once, but is used ${count} times`);
  });
  if (errors.length > 0) return invalid(errors.map((message) => ({ field: 'manifest', message })));
  return tests;
}

/** `bytes=a-b`, `bytes=a-` or `bytes=-n`; undefined when absent or not understood. */
function range(header: string | null, size: number): [number, number] | undefined {
  const match = header?.match(/^bytes=(\d*)-(\d*)$/);
  if (!match || (!match[1] && !match[2])) return undefined;
  const start = match[1] ? Number(match[1]) : Math.max(0, size - Number(match[2]));
  const end = match[1] && match[2] ? Math.min(Number(match[2]), size - 1) : size - 1;
  return start <= end ? [start, end] : undefined;
}

function download(request: Request, id: unknown, number: unknown, side: keyof MockTest) {
  const admin = requireAdmin();
  if (admin instanceof Response) return admin;
  const problem = find(id);
  if (problem instanceof Response) return problem;
  const n = Number(number);
  const test = problem.tests[n - 1];
  if (!Number.isInteger(n) || !test) return problemResponse(404, 'Not Found', 'Test not found.');

  const bytes = test[side];
  const name = `${n}.${side === 'input' ? 'in' : 'out'}`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/octet-stream',
    'Content-Disposition': `attachment; filename="${name}"`,
  };
  const part = range(request.headers.get('Range'), bytes.byteLength);
  if (!part) return new HttpResponse(bytes.slice(), { headers });
  const [start, end] = part;
  headers['Content-Range'] = `bytes ${start}-${end}/${bytes.byteLength}`;
  return new HttpResponse(bytes.slice(start, end + 1), { status: 206, headers });
}

export const adminProblemHandlers = [
  http.get('/api/admin/problems', async ({ request }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const url = new URL(request.url);
    const paging = parsePaging(url);
    if (paging instanceof Response) return paging;

    const q = url.searchParams.get('q')?.trim().toLowerCase() ?? '';
    const visibleParam = url.searchParams.get('visible');
    if (visibleParam !== null && visibleParam !== 'true' && visibleParam !== 'false') {
      return invalid([{ field: 'visible', message: 'has an invalid value' }]);
    }
    const items: AdminProblemSummary[] = problems
      .filter((p) => !q || p.title.toLowerCase().includes(q))
      .filter((p) => visibleParam === null || String(p.visible) === visibleParam)
      .map((p) => ({
        id: p.id,
        displayId: p.displayId,
        slug: p.slug,
        title: p.title,
        difficulty: p.difficulty,
        visible: p.visible,
        testCount: p.testCount,
        updatedAt: p.updatedAt,
      }));
    return HttpResponse.json(paginate(items, paging.page, paging.size));
  }),

  http.post('/api/admin/problems', async ({ request }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;

    const form = await request.formData();
    const content = parseJson<ProblemContent>(form, 'problem');
    if (content instanceof Response) return content;
    const manifest = parseJson<TestManifest>(form, 'manifest');
    if (manifest instanceof Response) return manifest;
    const errors = validateContent(content);
    if (errors.length > 0) return invalid(errors);
    const tests = await assemble(manifest, undefined, form.getAll('files') as File[]);
    if (tests instanceof Response) return tests;

    const problem: MockProblem = {
      id: Math.max(0, ...problems.map((p) => p.id)) + 1,
      ...contentFields(content),
      visible: false,
      languages: ['CPP17'],
      tests,
      testCount: tests.length,
      testdataVersion: testdataVersion(tests),
      revision: 1,
      updatedAt: new Date().toISOString(),
    };
    problems.push(problem);
    audit(admin, problem, { action: 'PROBLEM_CREATED' });
    return HttpResponse.json(toAdminProblem(problem), {
      status: 201,
      headers: { Location: `/api/admin/problems/${problem.id}` },
    });
  }),

  http.get('/api/admin/problems/:id', async ({ params }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const problem = find(params.id);
    if (problem instanceof Response) return problem;
    return HttpResponse.json(toAdminProblem(problem));
  }),

  http.put('/api/admin/problems/:id', async ({ request, params }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;
    const problem = find(params.id);
    if (problem instanceof Response) return problem;

    const body = (await request.json().catch(() => ({}))) as {
      revision?: number;
      content?: ProblemContent;
    };
    if (!isInt(body.revision)) return invalid([{ field: 'revision', message: 'is required' }]);
    if (!body.content) return invalid([{ field: 'content', message: 'is required' }]);
    const errors = validateContent(body.content, problem);
    if (errors.length > 0) return invalid(errors);
    if (body.revision !== problem.revision) {
      return problemResponse(
        409,
        'Conflict',
        'Someone else changed this problem after you opened it. Reload it to see their changes.',
      );
    }
    const next = contentFields(body.content);
    if (JSON.stringify(toContent(problem)) === JSON.stringify(toContent({ ...problem, ...next }))) {
      return HttpResponse.json(toAdminProblem(problem));
    }
    Object.assign(problem, next, {
      revision: problem.revision + 1,
      updatedAt: new Date().toISOString(),
    });
    audit(admin, problem, { action: 'PROBLEM_UPDATED' });
    return HttpResponse.json(toAdminProblem(problem));
  }),

  http.get('/api/admin/problems/:id/tests', async ({ params }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const problem = find(params.id);
    if (problem instanceof Response) return problem;
    return HttpResponse.json(toTestSet(problem));
  }),

  http.post('/api/admin/problems/:id/tests', async ({ request, params }) => {
    await latency();
    const admin = requireAdmin();
    if (admin instanceof Response) return admin;
    const csrfError = checkCsrf(request);
    if (csrfError) return csrfError;
    const problem = find(params.id);
    if (problem instanceof Response) return problem;

    const form = await request.formData();
    const manifest = parseJson<TestManifest>(form, 'manifest');
    if (manifest instanceof Response) return manifest;
    if (!isString(manifest.baseVersion)) {
      return invalid([{ field: 'manifest', message: 'baseVersion is required' }]);
    }
    if (manifest.baseVersion !== problem.testdataVersion) {
      return problemResponse(
        409,
        'Conflict',
        'Someone else saved new tests for this problem after you opened it. Reload the tests to see their changes.',
      );
    }
    const tests = await assemble(manifest, problem, form.getAll('files') as File[]);
    if (tests instanceof Response) return tests;

    const version = testdataVersion(tests);
    let changed = false;
    if (version !== problem.testdataVersion) {
      audit(admin, problem, {
        action: 'TESTS_UPDATED',
        fromTestCount: problem.testCount,
        toTestCount: tests.length,
      });
      Object.assign(problem, {
        tests,
        testCount: tests.length,
        testdataVersion: version,
        updatedAt: new Date().toISOString(),
      });
      changed = true;
    }
    return HttpResponse.json<AdminTestSaveResult>({ changed, testSet: toTestSet(problem) });
  }),

  http.get('/api/admin/problems/:id/tests/:number/input', async ({ request, params }) => {
    await latency();
    return download(request, params.id, params.number, 'input');
  }),

  http.get('/api/admin/problems/:id/tests/:number/output', async ({ request, params }) => {
    await latency();
    return download(request, params.id, params.number, 'output');
  }),
];
