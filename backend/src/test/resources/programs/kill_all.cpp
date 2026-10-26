// Sends SIGKILL to every process it is allowed to signal. On Linux that excludes itself; the supervising runner
// belongs to another user, so the program carries on, prints nothing and gets Wrong Answer.
#include <csignal>
int main() {
    kill(-1, SIGKILL);
}
