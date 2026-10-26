// Prints "isolated" only if no address can be reached: the internet, the Docker bridge gateway, the host.
#include <arpa/inet.h>
#include <cstdio>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

static bool reachable(const char *ip, int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return false;
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_port = htons(port);
    inet_pton(AF_INET, ip, &address.sin_addr);
    bool connected = connect(fd, reinterpret_cast<sockaddr *>(&address), sizeof address) == 0;
    close(fd);
    return connected;
}

int main() {
    const char *targets[] = {"1.1.1.1", "8.8.8.8", "172.17.0.1", "192.168.65.254", "10.0.2.2", "127.0.0.1"};
    const int ports[] = {80, 53, 5432, 5432, 5432, 5432};
    for (int i = 0; i < 6; i++) {
        if (reachable(targets[i], ports[i])) {
            printf("reached %s:%d\n", targets[i], ports[i]);
            return 0;
        }
    }
    printf("isolated\n");
}
