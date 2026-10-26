// About 200 ms of pure computation, for timing stability checks.
#include <bits/stdc++.h>
int main() {
    long long a, b;
    std::cin >> a >> b;
    volatile unsigned long long x = 0;
    for (unsigned long long i = 0; i < 400000000ULL; ++i) x = x + i;
    std::cout << a + b << "\n";
}
