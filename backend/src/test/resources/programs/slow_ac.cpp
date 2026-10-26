// Correct, but takes a moment, so that another submission is judged at the same time.
#include <bits/stdc++.h>
#include <unistd.h>
int main() {
    long long a, b;
    std::cin >> a >> b;
    usleep(300 * 1000);
    std::cout << a + b << "\n";
}
