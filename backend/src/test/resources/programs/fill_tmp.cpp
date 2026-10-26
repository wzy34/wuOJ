// Writes files to /tmp until a write fails; "capped" means the writable area stopped it well below 100 MB.
#include <csignal>
#include <cstdio>
#include <string>
#include <vector>
int main() {
    signal(SIGXFSZ, SIG_IGN);  // let a file-size limit show up as a failed write instead of a signal
    std::vector<char> chunk(1 << 20, 'x');
    long long written = 0;
    for (int file = 0; written < (200LL << 20); file++) {
        FILE *f = fopen(("/tmp/fill" + std::to_string(file)).c_str(), "w");
        if (!f) break;
        bool failed = false;
        for (int i = 0; i < 8 && !failed; i++) {
            size_t n = fwrite(chunk.data(), 1, chunk.size(), f);
            written += n;
            failed = n < chunk.size() || fflush(f) != 0;
        }
        fclose(f);
        if (failed) break;
    }
    printf(written < (100LL << 20) ? "capped\n" : "uncapped %lld\n", written);
}
