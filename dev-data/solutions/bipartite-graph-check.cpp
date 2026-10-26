#include <bits/stdc++.h>
int main() {
    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);
    int n, m;
    std::cin >> n >> m;
    std::vector<std::vector<int>> adjacent(n + 1);
    for (int i = 0; i < m; i++) {
        int u, v;
        std::cin >> u >> v;
        adjacent[u].push_back(v);
        adjacent[v].push_back(u);
    }
    std::vector<int> colour(n + 1, -1);
    for (int start = 1; start <= n; start++) {
        if (colour[start] != -1) continue;
        colour[start] = 0;
        std::queue<int> queue;
        queue.push(start);
        while (!queue.empty()) {
            int u = queue.front();
            queue.pop();
            for (int v : adjacent[u]) {
                if (colour[v] == -1) {
                    colour[v] = colour[u] ^ 1;
                    queue.push(v);
                } else if (colour[v] == colour[u]) {
                    std::cout << "NO\n";
                    return 0;
                }
            }
        }
    }
    std::cout << "YES\n";
}
