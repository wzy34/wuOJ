#include <bits/stdc++.h>
int main() {
    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);
    int n;
    std::cin >> n;
    std::vector<int> tree(n + 1, 0);
    long long inversions = 0;
    for (int i = 0; i < n; i++) {
        int p;
        std::cin >> p;
        int smaller_or_equal = 0;
        for (int x = p; x > 0; x -= x & -x) smaller_or_equal += tree[x];
        inversions += i - smaller_or_equal;
        for (int x = p; x <= n; x += x & -x) tree[x]++;
    }
    std::cout << inversions << "\n";
}
