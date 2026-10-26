#include <bits/stdc++.h>
int main() {
    int n;
    std::cin >> n;
    std::vector<bool> composite(n + 1, false);
    int count = 0;
    for (long long i = 2; i <= n; i++) {
        if (composite[i]) continue;
        count++;
        for (long long j = i * i; j <= n; j += i) composite[j] = true;
    }
    std::cout << count << "\n";
}
