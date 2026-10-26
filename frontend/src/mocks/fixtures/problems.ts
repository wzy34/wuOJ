import type { Difficulty, Problem } from '@/api/generated/model';

/** A problem as stored by the mock backend: the API shape plus how many hidden tests it has. */
export type MockProblem = Omit<Problem, 'status'> & { testCount: number };

type Draft = Pick<MockProblem, 'slug' | 'title' | 'difficulty' | 'statement' | 'samples'> &
  Partial<Pick<MockProblem, 'timeLimitMs' | 'memoryLimitMb' | 'testCount'>>;

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

export const problemFixtures: MockProblem[] = [...featured, ...warmUps].map((draft, index) => ({
  id: index + 1,
  displayId: String(1001 + index),
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  testCount: 10,
  languages: ['CPP17'],
  ...draft,
}));
