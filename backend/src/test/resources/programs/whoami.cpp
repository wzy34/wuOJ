// Prints its uid, its effective capabilities and whether it can gain privileges.
#include <fstream>
#include <iostream>
#include <string>
#include <unistd.h>
int main() {
    std::ifstream status("/proc/self/status");
    std::string line, caps = "?", nnp = "?";
    while (std::getline(status, line)) {
        if (line.rfind("CapEff:", 0) == 0) caps = line.substr(line.find_first_not_of(" \t", 7));
        if (line.rfind("NoNewPrivs:", 0) == 0) nnp = line.substr(line.find_first_not_of(" \t", 11));
    }
    std::cout << getuid() << " " << caps << " " << nnp << "\n";
}
