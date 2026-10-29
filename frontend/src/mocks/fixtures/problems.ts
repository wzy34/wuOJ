import type { Difficulty, Problem } from '@/api/generated/model';

/** One hidden test, byte for byte. */
export type MockTest = { input: Uint8Array; output: Uint8Array };

/**
 * A problem as stored by the mock backend: the API shape plus its visibility, edit bookkeeping and
 * hidden tests. `testCount` always equals `tests.length`.
 */
export type MockProblem = Omit<Problem, 'status'> & {
  testCount: number;
  visible: boolean;
  revision: number;
  updatedAt: string;
  testdataVersion: string;
  tests: MockTest[];
};

type Draft = Pick<MockProblem, 'slug' | 'title' | 'difficulty' | 'statement' | 'samples'> &
  Partial<Pick<MockProblem, 'timeLimitMs' | 'memoryLimitMb' | 'testCount' | 'visible'>>;

const featured: Draft[] = [
  {
    slug: 'a-plus-b',
    title: 'A + B',
    difficulty: 'EASY',
    testCount: 5,
    statement: `Given two integers $a$ and $b$, print their sum.

## Input

A single line with two integers $a$ and $b$ ($-10^9 \\le a, b \\le 10^9$).

## Output

Print $a + b$.

## Notes

The sum can exceed the range of a 32-bit \`int\`, so use \`long long\`.
New to C++ input and output? See [cppreference: Input/output library](https://en.cppreference.com/w/cpp/io).`,
    samples: [
      { input: '1 2\n', output: '3\n' },
      { input: '-5 12\n', output: '7\n' },
    ],
  },
  {
    slug: 'maximum-subarray-sum',
    title: 'Maximum Subarray Sum',
    difficulty: 'MEDIUM',
    testCount: 12,
    statement: `You are given an array $a_1, a_2, \\ldots, a_n$. Find

$$
\\max_{1 \\le l \\le r \\le n} \\sum_{i=l}^{r} a_i.
$$

## Input

The first line contains $n$ ($1 \\le n \\le 2 \\cdot 10^5$).
The second line contains $n$ integers $a_i$ ($|a_i| \\le 10^9$).

## Output

Print the maximum sum of a non-empty contiguous subarray.`,
    samples: [
      { input: '5\n-2 1 -3 4 -1\n', output: '4\n' },
      { input: '3\n-7 -2 -9\n', output: '-2\n' },
    ],
  },
  {
    slug: 'prime-sieve',
    title: 'Prime Sieve',
    difficulty: 'EASY',
    testCount: 8,
    statement: `Count the prime numbers that are less than or equal to $n$.

## Input

One integer $n$ ($1 \\le n \\le 10^7$).

## Output

The number of primes $p \\le n$.

## Examples

| $n$ | Answer |
| --- | --- |
| 1 | 0 |
| 10 | 4 |
| 100 | 25 |`,
    samples: [{ input: '10\n', output: '4\n' }],
  },
  {
    slug: 'bipartite-graph-check',
    title: 'Bipartite Graph Check',
    difficulty: 'MEDIUM',
    testCount: 10,
    statement: `An undirected graph with $n$ vertices and $m$ edges is given. Decide whether its vertices can be
coloured with two colours so that every edge joins vertices of different colours.

## Input

The first line contains $n$ and $m$ ($1 \\le n \\le 10^5$, $0 \\le m \\le 2 \\cdot 10^5$).
Each of the next $m$ lines contains an edge $u\\ v$ ($1 \\le u, v \\le n$).

## Output

Print \`YES\` if the graph is bipartite, otherwise \`NO\`.`,
    samples: [
      { input: '3 2\n1 2\n2 3\n', output: 'YES\n' },
      { input: '3 3\n1 2\n2 3\n3 1\n', output: 'NO\n' },
    ],
  },
  {
    slug: 'shortest-path-in-a-grid',
    title: 'Shortest Path in a Grid',
    difficulty: 'MEDIUM',
    testCount: 10,
    timeLimitMs: 2000,
    statement: `A grid has $r$ rows and $c$ columns. Cells marked \`#\` are walls, \`.\` are free, \`S\` is the
start and \`T\` is the target. Each move goes to an adjacent free cell (up, down, left or right).

Find the minimum number of moves from \`S\` to \`T\`, or $-1$ if \`T\` cannot be reached.

## Input

The first line contains $r$ and $c$ ($1 \\le r, c \\le 1000$), followed by $r$ lines of the grid, e.g.

\`\`\`
S.#
..#
#.T
\`\`\`

## Output

The minimum number of moves, or \`-1\`.`,
    samples: [{ input: '3 3\nS.#\n..#\n#.T\n', output: '4\n' }],
  },
  {
    slug: 'counting-inversions',
    title: 'Counting Inversions',
    difficulty: 'HARD',
    testCount: 15,
    memoryLimitMb: 128,
    statement: `An inversion in a permutation $p_1, \\ldots, p_n$ is a pair $(i, j)$ with $i < j$ and $p_i > p_j$.
Count the inversions.

## Input

The first line contains $n$ ($1 \\le n \\le 10^6$); the second line contains the permutation.

## Output

The number of inversions. It can be as large as $\\frac{n(n-1)}{2}$.`,
    samples: [{ input: '5\n2 4 1 3 5\n', output: '3\n' }],
  },
];

const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD'];

/** Simple filler problems so that the list has enough rows to page through. */
const warmUps: Draft[] = Array.from({ length: 39 }, (_, index) => {
  const n = index + 1;
  const factor = n + 1;
  return {
    slug: `warm-up-${n}`,
    title: `Warm-up ${n}: Multiply by ${factor}`,
    difficulty: difficulties[index % 3],
    testCount: 4,
    statement: `Read an integer $x$ ($|x| \\le 10^6$) and print $${factor} \\times x$.`,
    samples: [{ input: '3\n', output: `${3 * factor}\n` }],
  };
});

/** Not published yet: only admins see it. */
const hidden: Draft[] = [
  {
    slug: 'shortest-path-draft',
    title: 'Shortest Path (draft)',
    difficulty: 'HARD',
    testCount: 3,
    visible: false,
    statement: `Find the length of the shortest path from vertex $1$ to vertex $n$ in a weighted graph.

## Input

The first line contains $n$ and $m$ ($2 \\le n \\le 10^5$, $1 \\le m \\le 2 \\cdot 10^5$).`,
    samples: [{ input: '2 1\n1 2 5\n', output: '5\n' }],
  },
];

const encoder = new TextEncoder();

/** Seeded tests are generated text: test `n` reads `n` and expects a line naming the problem. */
function seedTests(slug: string, count: number): MockTest[] {
  return Array.from({ length: count }, (_, i) => ({
    input: encoder.encode(`${i + 1}\n`),
    output: encoder.encode(`${slug} ${i + 1}\n`),
  }));
}

/** A version name derived from the tests' contents (FNV-1a), so identical tests give the same one. */
export function testdataVersion(tests: MockTest[]): string {
  let hash = 0x811c9dc5;
  const mix = (byte: number) => {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  };
  for (const test of tests) {
    for (const part of [test.input, test.output]) {
      part.forEach(mix);
      mix(0xff);
    }
  }
  return `v${tests.length}x${hash.toString(16)}`;
}

/** Fresh copies, so edits made in the mock never leak into the next test or reload. */
export function seedProblems(): MockProblem[] {
  return [...featured, ...warmUps, ...hidden].map((draft, index) => {
    const tests = seedTests(draft.slug, draft.testCount ?? 10);
    return {
      id: index + 1,
      displayId: String(1001 + index),
      timeLimitMs: 1000,
      memoryLimitMb: 256,
      languages: ['CPP17'],
      visible: true,
      ...draft,
      samples: draft.samples.map((s) => ({ ...s })),
      testCount: tests.length,
      tests,
      testdataVersion: testdataVersion(tests),
      revision: 1,
      updatedAt: new Date(Date.UTC(2026, 9, 1)).toISOString(),
    };
  });
}

/** The seeded problems, for tests that need to know what the mock starts with. */
export const problemFixtures: MockProblem[] = seedProblems();
