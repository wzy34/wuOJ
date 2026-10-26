// Counts the C++ sources it can see; only its own /work/main.cpp should be visible.
#include <filesystem>
#include <iostream>
namespace fs = std::filesystem;
int main() {
    int count = 0;
    std::error_code ec;
    for (auto it = fs::recursive_directory_iterator("/", fs::directory_options::skip_permission_denied, ec);
         it != fs::recursive_directory_iterator(); it.increment(ec)) {
        if (ec) { ec.clear(); continue; }
        std::string p = it->path().string();
        if (p == "/proc" || p == "/sys" || p == "/dev") { it.disable_recursion_pending(); continue; }
        if (it->path().filename() == "main.cpp") count++;
    }
    std::cout << count << "\n";
}
