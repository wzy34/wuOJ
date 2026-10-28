import type { AuditAction, SubmissionDetail, UserRole } from '@/api/generated/model';
import { problemFixtures, type MockProblem } from './fixtures/problems';

export type MockUser = {
  id: number;
  username: string;
  password: string;
  role: UserRole;
  enabled: boolean;
  mustChangePassword: boolean;
  /** Sessions remember the epoch they were created in; incrementing it ends them all. */
  epoch: number;
  createdAt: string;
  lastLoginAt?: string;
};

export type MockAuditEntry = {
  id: number;
  createdAt: string;
  actorId: number;
  targetId: number;
  action: AuditAction;
  fromRole?: UserRole;
  toRole?: UserRole;
};

/** A stored submission always carries source code; owner-only fields are stripped per request. */
export type MockSubmission = SubmissionDetail & { sourceCode: string };

/** Knobs that tests turn down to keep runs fast; the browser uses the defaults. */
export const mockConfig = {
  latencyMs: 0,
  judgeStepMs: 0,
  rateLimitMax: 0,
  rateLimitWindowMs: 0,
  registrationEnabled: true,
};

export function resetMockConfig(): void {
  const isTest = import.meta.env.MODE === 'test';
  mockConfig.latencyMs = isTest ? 0 : 150;
  mockConfig.judgeStepMs = isTest ? 5 : 350;
  mockConfig.rateLimitMax = 3;
  mockConfig.rateLimitWindowMs = 10_000;
  mockConfig.registrationEnabled = true;
}
resetMockConfig();

export const DEMO_USER = { username: 'student', password: 'student' };
export const DEMO_ADMIN = { username: 'admin', password: 'admin' };

function daysAgo(days: number): string {
  return new Date(Date.now() - days * 86_400_000).toISOString();
}

function seedUsers(): MockUser[] {
  const account = (id: number, username: string, role: UserRole, joined: number): MockUser => ({
    id,
    username,
    password: username,
    role,
    enabled: true,
    mustChangePassword: false,
    epoch: 0,
    createdAt: daysAgo(joined),
  });
  return [
    account(1, DEMO_USER.username, 'USER', 30),
    account(2, 'alice', 'USER', 21),
    account(3, 'bob', 'USER', 14),
    account(4, DEMO_ADMIN.username, 'ADMIN', 60),
  ];
}

/** Accounts live in memory only: registrations and admin changes last for the page session. */
export const users: MockUser[] = seedUsers();

export const problems: MockProblem[] = problemFixtures;

const SESSION_KEY = 'wuoj-mock-session';
const DB_KEY = 'wuoj-mock-db-v1';

const ACCEPTED_SOURCE = `#include <bits/stdc++.h>
using namespace std;

int main() {
    long long a, b;
    cin >> a >> b;
    cout << a + b << '\\n';
}
`;

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString();
}

function seedSubmissions(): MockSubmission[] {
  const ref = (slug: string) => {
    const problem = problems.find((p) => p.slug === slug)!;
    return {
      id: problem.id,
      displayId: problem.displayId,
      slug: problem.slug,
      title: problem.title,
    };
  };
  const user = (id: number) => {
    const found = users.find((u) => u.id === id)!;
    return { id: found.id, username: found.username };
  };
  const base = { language: 'CPP17' as const, status: 'FINISHED' as const };
  return [
    {
      ...base,
      id: 1,
      user: user(1),
      problem: ref('a-plus-b'),
      verdict: 'WA',
      timeMs: 3,
      memoryKb: 3412,
      createdAt: hoursAgo(30),
      testResults: [
        { testNumber: 1, verdict: 'AC', timeMs: 2, memoryKb: 3400 },
        { testNumber: 2, verdict: 'WA', timeMs: 3, memoryKb: 3412 },
      ],
      sourceCode: ACCEPTED_SOURCE.replace('long long', 'int'),
    },
    {
      ...base,
      id: 2,
      user: user(1),
      problem: ref('a-plus-b'),
      verdict: 'AC',
      timeMs: 4,
      memoryKb: 3420,
      createdAt: hoursAgo(29),
      testResults: [1, 2, 3, 4, 5].map((n) => ({
        testNumber: n,
        verdict: 'AC' as const,
        timeMs: 2 + (n % 3),
        memoryKb: 3400 + n * 4,
      })),
      sourceCode: ACCEPTED_SOURCE,
    },
    {
      ...base,
      id: 3,
      user: user(2),
      problem: ref('a-plus-b'),
      verdict: 'AC',
      timeMs: 5,
      memoryKb: 3388,
      createdAt: hoursAgo(26),
      testResults: [1, 2, 3, 4, 5].map((n) => ({
        testNumber: n,
        verdict: 'AC' as const,
        timeMs: 3 + (n % 3),
        memoryKb: 3380 + n,
      })),
      sourceCode: ACCEPTED_SOURCE,
    },
    {
      ...base,
      id: 4,
      user: user(3),
      problem: ref('prime-sieve'),
      verdict: 'CE',
      createdAt: hoursAgo(20),
      testResults: [],
      compileOutput:
        "main.cpp: In function 'int main()':\nmain.cpp:6:5: error: 'cot' was not declared in this scope; did you mean 'cout'?\n    6 |     cot << count << '\\n';\n      |     ^~~\n      |     cout",
      sourceCode:
        "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    int count = 0;\n    cot << count << '\\n';\n}\n",
    },
    {
      ...base,
      id: 5,
      user: user(1),
      problem: ref('maximum-subarray-sum'),
      verdict: 'TLE',
      timeMs: 1000,
      memoryKb: 5120,
      createdAt: hoursAgo(5),
      testResults: [
        { testNumber: 1, verdict: 'AC', timeMs: 4, memoryKb: 3500 },
        { testNumber: 2, verdict: 'AC', timeMs: 6, memoryKb: 3520 },
        { testNumber: 3, verdict: 'TLE', timeMs: 1000, memoryKb: 5120 },
      ],
      sourceCode:
        '#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    // O(n^2) brute force\n}\n',
    },
    {
      ...base,
      id: 6,
      user: user(2),
      problem: ref('bipartite-graph-check'),
      verdict: 'WA',
      timeMs: 41,
      memoryKb: 9800,
      createdAt: hoursAgo(2),
      testResults: [
        { testNumber: 1, verdict: 'AC', timeMs: 3, memoryKb: 3500 },
        { testNumber: 2, verdict: 'WA', timeMs: 41, memoryKb: 9800 },
      ],
      sourceCode: '#include <bits/stdc++.h>\nint main() {}\n',
    },
  ];
}

type Persisted = { submissions: MockSubmission[]; nextSubmissionId: number };

function readStorage(key: string): string | null {
  try {
    return globalThis.localStorage?.getItem(key) ?? null;
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) globalThis.localStorage?.removeItem(key);
    else globalThis.localStorage?.setItem(key, value);
  } catch {
    // Storage unavailable: the mock keeps working in memory only.
  }
}

function load(): Persisted {
  const raw = readStorage(DB_KEY);
  if (raw) {
    try {
      return JSON.parse(raw) as Persisted;
    } catch {
      // Corrupt data: start over from the seed.
    }
  }
  return { submissions: seedSubmissions(), nextSubmissionId: 7 };
}

export const db = {
  ...load(),
  audit: [] as MockAuditEntry[],
  nextUserId: 5,
  csrfToken: null as string | null,
  /** Submission timestamps per user, for rate limiting. */
  recentSubmits: new Map<number, number[]>(),
};

export function saveDb(): void {
  const data: Persisted = { submissions: db.submissions, nextSubmissionId: db.nextSubmissionId };
  writeStorage(DB_KEY, JSON.stringify(data));
}

/**
 * The session's user, as the real server checks it: a session ends when its account is gone or
 * disabled, or when the account's epoch moved on.
 */
export function getSessionUser(): MockUser | undefined {
  const [id, epoch] = (readStorage(SESSION_KEY) ?? '').split(':').map(Number);
  const user = users.find((u) => u.id === id);
  if (!user) return undefined;
  if (!user.enabled || user.epoch !== epoch) {
    setSessionUser(null);
    return undefined;
  }
  return user;
}

export function setSessionUser(user: MockUser | null): void {
  writeStorage(SESSION_KEY, user ? `${user.id}:${user.epoch}` : null);
}

/** Restores the seeded state and signs out; used between tests. */
export function resetDb(): void {
  writeStorage(DB_KEY, null);
  writeStorage(SESSION_KEY, null);
  db.submissions = seedSubmissions();
  db.nextSubmissionId = 7;
  users.splice(0, users.length, ...seedUsers());
  db.audit = [];
  db.nextUserId = 5;
  db.csrfToken = null;
  db.recentSubmits.clear();
}
