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
