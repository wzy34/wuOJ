#include <cstdio>
#include <vector>
int main() {
    std::vector<char> memory(512u << 20, 1);  // 512 MB, every page touched
    printf("%d\n", memory[12345]);
}
