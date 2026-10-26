#include <bits/stdc++.h>
int main() {
    int rows, cols;
    std::cin >> rows >> cols;
    std::vector<std::string> grid(rows);
    for (auto &row : grid) std::cin >> row;
    std::vector<std::vector<int>> distance(rows, std::vector<int>(cols, -1));
    std::queue<std::pair<int, int>> queue;
    for (int r = 0; r < rows; r++)
        for (int c = 0; c < cols; c++)
            if (grid[r][c] == 'S') {
                distance[r][c] = 0;
                queue.push({r, c});
            }
    const int dr[] = {1, -1, 0, 0}, dc[] = {0, 0, 1, -1};
    while (!queue.empty()) {
        auto [r, c] = queue.front();
        queue.pop();
        if (grid[r][c] == 'T') {
            std::cout << distance[r][c] << "\n";
            return 0;
        }
        for (int d = 0; d < 4; d++) {
            int nr = r + dr[d], nc = c + dc[d];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols || grid[nr][nc] == '#' || distance[nr][nc] != -1)
                continue;
            distance[nr][nc] = distance[r][c] + 1;
            queue.push({nr, nc});
        }
    }
    std::cout << -1 << "\n";
}
