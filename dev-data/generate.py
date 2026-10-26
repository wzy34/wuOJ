#!/usr/bin/env python3
"""Generates the development problem packages in dev-data/problems/.

The six problems match the featured problems of the web app's mock mode (same slugs, display ids, statements and
samples), with real hidden tests computed here. Output is deterministic; rerun after changing this file:

    python3 dev-data/generate.py
"""
import random
import shutil
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "problems"


def write_package(slug, display_id, title, difficulty, statement, samples, tests, time_limit_ms=1000,
                  memory_limit_mb=256):
    directory = ROOT / slug
    if directory.exists():
        shutil.rmtree(directory)
    (directory / "samples").mkdir(parents=True)
    (directory / "tests").mkdir()
    (directory / "problem.yaml").write_text(
        f'displayId: "{display_id}"\nslug: {slug}\ntitle: {title}\ndifficulty: {difficulty}\n'
        f"timeLimitMs: {time_limit_ms}\nmemoryLimitMb: {memory_limit_mb}\nvisible: true\n")
    (directory / "statement.md").write_text(statement.strip() + "\n")
    for n, (given, expected) in enumerate(samples, 1):
        (directory / "samples" / f"{n}.in").write_text(given)
        (directory / "samples" / f"{n}.out").write_text(expected)
    for n, (given, expected) in enumerate(tests, 1):
        (directory / "tests" / f"{n}.in").write_text(given)
        (directory / "tests" / f"{n}.out").write_text(expected)


def a_plus_b():
    rng = random.Random(1001)
    pairs = [(1, 2), (-5, 12), (0, 0), (10**9, 10**9), (-10**9, -10**9), (2147483647, 1)]
    pairs += [(rng.randint(-10**9, 10**9), rng.randint(-10**9, 10**9)) for _ in range(2)]
    tests = [(f"{a} {b}\n", f"{a + b}\n") for a, b in pairs]
    write_package("a-plus-b", "1001", "A + B", "EASY", r"""
Given two integers $a$ and $b$, print their sum.

## Input

A single line with two integers $a$ and $b$ ($-10^9 \le a, b \le 10^9$).

## Output

Print $a + b$.

## Notes

The sum can exceed the range of a 32-bit `int`, so use `long long`.
New to C++ input and output? See [cppreference: Input/output library](https://en.cppreference.com/w/cpp/io).
""", [("1 2\n", "3\n"), ("-5 12\n", "7\n")], tests)


def maximum_subarray_sum():
    rng = random.Random(1002)

    def case(values):
        best, current = values[0], 0
        for v in values:
            current = max(v, current + v)
            best = max(best, current)
        return f"{len(values)}\n{' '.join(map(str, values))}\n", f"{best}\n"

    tests = [case([-2, 1, -3, 4, -1]), case([-7, -2, -9]), case([5]), case([-10**9])]
    for size, low, high in [(10, -10, 10), (1000, -100, 100), (5000, -10**9, 10**9), (50000, -10**9, 10**9),
                            (50000, -1000, 5), (50000, 1, 10**9)]:
        tests.append(case([rng.randint(low, high) for _ in range(size)]))
    write_package("maximum-subarray-sum", "1002", "Maximum Subarray Sum", "MEDIUM", r"""
You are given an array $a_1, a_2, \ldots, a_n$. Find

$$
\max_{1 \le l \le r \le n} \sum_{i=l}^{r} a_i.
$$

## Input

The first line contains $n$ ($1 \le n \le 2 \cdot 10^5$).
The second line contains $n$ integers $a_i$ ($|a_i| \le 10^9$).

## Output

Print the maximum sum of a non-empty contiguous subarray.
""", [("5\n-2 1 -3 4 -1\n", "4\n"), ("3\n-7 -2 -9\n", "-2\n")], tests)


def prime_sieve():
    limit = 10**7
    sieve = bytearray([1]) * (limit + 1)
    sieve[0] = sieve[1] = 0
    for i in range(2, int(limit**0.5) + 1):
        if sieve[i]:
            sieve[i * i::i] = bytearray(len(range(i * i, limit + 1, i)))
    counts = {}
    running = 0
    queries = sorted({1, 2, 10, 100, 9973, 1_000_000, 7_654_321, limit})
    remaining = list(queries)
    for i in range(limit + 1):
        running += sieve[i]
        if remaining and i == remaining[0]:
            counts[i] = running
            remaining.pop(0)
    tests = [(f"{n}\n", f"{counts[n]}\n") for n in queries]
    write_package("prime-sieve", "1003", "Prime Sieve", "EASY", r"""
Count the prime numbers that are less than or equal to $n$.

## Input

One integer $n$ ($1 \le n \le 10^7$).

## Output

The number of primes $p \le n$.

## Examples

| $n$ | Answer |
| --- | --- |
| 1 | 0 |
| 10 | 4 |
| 100 | 25 |
""", [("10\n", "4\n")], tests)


def bipartite_graph_check():
    rng = random.Random(1004)

    def answer(n, edges):
        adjacent = [[] for _ in range(n + 1)]
        for u, v in edges:
            adjacent[u].append(v)
            adjacent[v].append(u)
        colour = [-1] * (n + 1)
        for start in range(1, n + 1):
            if colour[start] != -1:
                continue
            colour[start] = 0
            queue = deque([start])
            while queue:
                u = queue.popleft()
                for v in adjacent[u]:
                    if colour[v] == -1:
                        colour[v] = colour[u] ^ 1
                        queue.append(v)
                    elif colour[v] == colour[u]:
                        return "NO"
        return "YES"

    def case(n, edges):
        lines = [f"{n} {len(edges)}"] + [f"{u} {v}" for u, v in edges]
        return "\n".join(lines) + "\n", answer(n, edges) + "\n"

    def two_sided(n, m):
        left = rng.sample(range(1, n + 1), n // 2)
        left_set = set(left)
        right = [v for v in range(1, n + 1) if v not in left_set]
        return [(rng.choice(left), rng.choice(right)) for _ in range(m)]

    tests = [case(3, [(1, 2), (2, 3)]), case(3, [(1, 2), (2, 3), (3, 1)]), case(1, []), case(2, [(1, 1)])]
    tests.append(case(1000, two_sided(1000, 3000)))
    odd_cycle = two_sided(1000, 3000) + [(1, 2), (2, 3), (3, 1)]
    tests.append(case(1000, odd_cycle))
    tests.append(case(100_000, two_sided(100_000, 50_000)))
    tests.append(case(100_000, [(i, i + 1) for i in range(1, 50_000)]))
    tests.append(case(100_000, [(i, i + 1) for i in range(1, 50_000)] + [(1, 49_999)]))
    tests.append(case(20_000, [(rng.randint(1, 20_000), rng.randint(1, 20_000)) for _ in range(50_000)]))
    write_package("bipartite-graph-check", "1004", "Bipartite Graph Check", "MEDIUM", r"""
An undirected graph with $n$ vertices and $m$ edges is given. Decide whether its vertices can be
coloured with two colours so that every edge joins vertices of different colours.

## Input

The first line contains $n$ and $m$ ($1 \le n \le 10^5$, $0 \le m \le 2 \cdot 10^5$).
Each of the next $m$ lines contains an edge $u\ v$ ($1 \le u, v \le n$).

## Output

Print `YES` if the graph is bipartite, otherwise `NO`.
""", [("3 2\n1 2\n2 3\n", "YES\n"), ("3 3\n1 2\n2 3\n3 1\n", "NO\n")], tests)


def shortest_path_in_a_grid():
    rng = random.Random(1005)

    def answer(grid):
        rows, cols = len(grid), len(grid[0])
        start = next((r, c) for r in range(rows) for c in range(cols) if grid[r][c] == "S")
        distance = {start: 0}
        queue = deque([start])
        while queue:
            r, c = queue.popleft()
            if grid[r][c] == "T":
                return distance[(r, c)]
            for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nr, nc = r + dr, c + dc
                if 0 <= nr < rows and 0 <= nc < cols and grid[nr][nc] != "#" and (nr, nc) not in distance:
                    distance[(nr, nc)] = distance[(r, c)] + 1
                    queue.append((nr, nc))
        return -1

    def case(grid):
        return f"{len(grid)} {len(grid[0])}\n" + "\n".join(grid) + "\n", f"{answer(grid)}\n"

    def random_grid(rows, cols, walls):
        cells = [["#" if rng.random() < walls else "." for _ in range(cols)] for _ in range(rows)]
        cells[0][0], cells[rows - 1][cols - 1] = "S", "T"
        return ["".join(row) for row in cells]

    tests = [case(["S.#", "..#", "#.T"]), case(["ST"]), case(["S#T"]), case(["S..", "###", "..T"])]
    tests += [case(random_grid(10, 10, 0.2)), case(random_grid(50, 80, 0.3)), case(random_grid(300, 300, 0.25)),
              case(random_grid(300, 300, 0.45)), case(random_grid(1, 1000, 0.0)), case(random_grid(1000, 1, 0.0))]
    write_package("shortest-path-in-a-grid", "1005", "Shortest Path in a Grid", "MEDIUM", r"""
A grid has $r$ rows and $c$ columns. Cells marked `#` are walls, `.` are free, `S` is the
start and `T` is the target. Each move goes to an adjacent free cell (up, down, left or right).

Find the minimum number of moves from `S` to `T`, or $-1$ if `T` cannot be reached.

## Input

The first line contains $r$ and $c$ ($1 \le r, c \le 1000$), followed by $r$ lines of the grid, e.g.

```
S.#
..#
#.T
```

## Output

The minimum number of moves, or `-1`.
""", [("3 3\nS.#\n..#\n#.T\n", "4\n")], tests, time_limit_ms=2000)


def counting_inversions():
    rng = random.Random(1006)

    def inversions(values):
        # Merge sort count.
        def sort(a):
            if len(a) <= 1:
                return a, 0
            middle = len(a) // 2
            left, x = sort(a[:middle])
            right, y = sort(a[middle:])
            merged, count, i, j = [], x + y, 0, 0
            while i < len(left) and j < len(right):
                if left[i] <= right[j]:
                    merged.append(left[i])
                    i += 1
                else:
                    merged.append(right[j])
                    count += len(left) - i
                    j += 1
            merged += left[i:] + right[j:]
            return merged, count
        return sort(values)[1]

    def case(values):
        return f"{len(values)}\n{' '.join(map(str, values))}\n", f"{inversions(values)}\n"

    def permutation(n):
        values = list(range(1, n + 1))
        rng.shuffle(values)
        return values

    tests = [case([2, 4, 1, 3, 5]), case([1]), case([2, 1]), case(list(range(1, 11))),
             case(list(range(10, 0, -1)))]
    tests += [case(permutation(n)) for n in (100, 1000, 5000, 20_000, 50_000, 100_000)]
    tests += [case(list(range(100_000, 0, -1))), case(list(range(1, 100_001))), case(permutation(100_000)),
              case(permutation(80_000))]
    write_package("counting-inversions", "1006", "Counting Inversions", "HARD", r"""
An inversion in a permutation $p_1, \ldots, p_n$ is a pair $(i, j)$ with $i < j$ and $p_i > p_j$.
Count the inversions.

## Input

The first line contains $n$ ($1 \le n \le 10^6$); the second line contains the permutation.

## Output

The number of inversions. It can be as large as $\frac{n(n-1)}{2}$.
""", [("5\n2 4 1 3 5\n", "3\n")], tests, memory_limit_mb=128)


if __name__ == "__main__":
    for generate in (a_plus_b, maximum_subarray_sum, prime_sieve, bipartite_graph_check, shortest_path_in_a_grid,
                     counting_inversions):
        generate()
        print("generated", generate.__name__.replace("_", "-"))
