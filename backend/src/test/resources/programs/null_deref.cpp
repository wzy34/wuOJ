#include <cstdio>
int main() {
    int *volatile p = nullptr;
    printf("%d\n", *p);
}
