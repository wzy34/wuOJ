#include <bits/stdc++.h>
int main() {
    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);
    int n;
    std::cin >> n;
    long long best = LLONG_MIN, current = 0;
    for (int i = 0; i < n; i++) {
        long long a;
        std::cin >> a;
        current = std::max(a, current + a);
        best = std::max(best, current);
    }
    std::cout << best << "\n";
}
