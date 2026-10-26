/*
 * oj-runner: runs one program inside a judge container under resource limits and reports what happened.
 *
 *   oj-runner idle
 *       PID 1 of a run container: sleeps, reaps orphans, exits on SIGTERM.
 *
 *   oj-runner run [options] -- program [args...]
 *       Runs the program once and prints one JSON line:
 *       {"exitCode":0,"signal":0,"cpuMs":12,"wallMs":15,"maxRssKb":3184,"wallTimedOut":false,"oomKilled":false,
 *        "error":null}
 *
 *       --cpu-ms N       CPU time limit (user + system); enforced with RLIMIT_CPU, rounded up plus one second
 *       --wall-ms N      wall-clock limit; the program's process group is killed when it is reached
 *       --fsize-bytes N  largest file the program may write (RLIMIT_FSIZE); exceeding it raises SIGXFSZ
 *       --stack-kb N     stack size (RLIMIT_STACK)
 *       --nofile N       open file limit (RLIMIT_NOFILE)
 *       --uid N --gid N  user and group to run the program as (needs CAP_SETUID and CAP_SETGID)
 *       --stdin F --stdout F --stderr F   files for the standard streams (default /dev/null)
 *
 * The runner only reports facts; deciding the verdict (TLE, MLE, OLE, RE, ...) is the judge's job. Measurements
 * come from wait4() for the program itself, so they are per run even though runs share a container. oomKilled comes
 * from the container's cgroup, so a program killed by the kernel for lack of memory is told apart from one that was
 * sent SIGKILL some other way.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <grp.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static char *const CLEAN_ENV[] = {
    "PATH=/usr/local/bin:/usr/bin:/bin", "HOME=/tmp", "TMPDIR=/tmp", "LANG=C.UTF-8", NULL};

struct options {
    long cpu_ms, wall_ms, fsize_bytes, stack_kb, nofile;
    long uid, gid;
    const char *in, *out, *err;
    char **argv;
};

static void usage(void) {
    fputs("usage: oj-runner idle | oj-runner run [options] -- program [args...]\n", stderr);
    exit(2);
}

static long number(const char *flag, const char *value) {
    char *end;
    errno = 0;
    long n = strtol(value, &end, 10);
    if (errno != 0 || *end != '\0' || n < 0) {
        fprintf(stderr, "oj-runner: invalid value for %s: %s\n", flag, value);
        exit(2);
    }
    return n;
}

static void parse(int argc, char **argv, struct options *o) {
    memset(o, 0, sizeof *o);
    o->uid = o->gid = -1;
    o->nofile = 64;
    o->in = o->out = o->err = "/dev/null";
    int i = 2;
    for (; i < argc; i++) {
        const char *a = argv[i];
        if (strcmp(a, "--") == 0) {
            i++;
            break;
        }
        if (i + 1 >= argc) usage();
        const char *v = argv[++i];
        if (strcmp(a, "--cpu-ms") == 0) o->cpu_ms = number(a, v);
        else if (strcmp(a, "--wall-ms") == 0) o->wall_ms = number(a, v);
        else if (strcmp(a, "--fsize-bytes") == 0) o->fsize_bytes = number(a, v);
        else if (strcmp(a, "--stack-kb") == 0) o->stack_kb = number(a, v);
        else if (strcmp(a, "--nofile") == 0) o->nofile = number(a, v);
        else if (strcmp(a, "--uid") == 0) o->uid = number(a, v);
        else if (strcmp(a, "--gid") == 0) o->gid = number(a, v);
        else if (strcmp(a, "--stdin") == 0) o->in = v;
        else if (strcmp(a, "--stdout") == 0) o->out = v;
        else if (strcmp(a, "--stderr") == 0) o->err = v;
        else usage();
    }
    if (i >= argc) usage();
    o->argv = &argv[i];
}

static long now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000L + ts.tv_nsec / 1000000L;
}

/* Tells the parent why the child could not start the program, then exits. */
static void child_fail(int report_fd, const char *what) {
    char message[256];
    int n = snprintf(message, sizeof message, "%s: %s", what, strerror(errno));
    if (n > 0) {
        ssize_t ignored = write(report_fd, message, (size_t) n);
        (void) ignored;
    }
    _exit(127);
}

static void set_limit(int report_fd, int resource, rlim_t soft, rlim_t hard, const char *name) {
    struct rlimit limit = {soft, hard};
    if (setrlimit(resource, &limit) != 0) child_fail(report_fd, name);
}

static void redirect(int report_fd, const char *path, int target, int flags) {
    int fd = open(path, flags | O_CLOEXEC, 0644);
    if (fd < 0) child_fail(report_fd, path);
    if (dup2(fd, target) < 0) child_fail(report_fd, "dup2");
    close(fd);
}

static void child(const struct options *o, int report_fd) {
    /* A process group of its own, so the whole tree can be killed at the wall-clock limit. */
    setpgid(0, 0);
    redirect(report_fd, o->in, STDIN_FILENO, O_RDONLY);
    redirect(report_fd, o->out, STDOUT_FILENO, O_WRONLY | O_CREAT | O_TRUNC);
    redirect(report_fd, o->err, STDERR_FILENO, O_WRONLY | O_CREAT | O_TRUNC);

    if (o->cpu_ms > 0) {
        rlim_t seconds = (rlim_t) ((o->cpu_ms + 999) / 1000 + 1);
        set_limit(report_fd, RLIMIT_CPU, seconds, seconds + 1, "RLIMIT_CPU");
    }
    if (o->fsize_bytes > 0) set_limit(report_fd, RLIMIT_FSIZE, (rlim_t) o->fsize_bytes, (rlim_t) o->fsize_bytes,
            "RLIMIT_FSIZE");
    if (o->stack_kb > 0) set_limit(report_fd, RLIMIT_STACK, (rlim_t) o->stack_kb * 1024,
            (rlim_t) o->stack_kb * 1024, "RLIMIT_STACK");
    set_limit(report_fd, RLIMIT_NOFILE, (rlim_t) o->nofile, (rlim_t) o->nofile, "RLIMIT_NOFILE");
    set_limit(report_fd, RLIMIT_CORE, 0, 0, "RLIMIT_CORE");

    if (o->gid >= 0) {
        if (setgroups(0, NULL) != 0) child_fail(report_fd, "setgroups");
        if (setgid((gid_t) o->gid) != 0) child_fail(report_fd, "setgid");
    }
    /* Switching from root to another uid also drops every capability. */
    if (o->uid >= 0 && setuid((uid_t) o->uid) != 0) child_fail(report_fd, "setuid");

    signal(SIGPIPE, SIG_DFL);
    execve(o->argv[0], o->argv, CLEAN_ENV);
    child_fail(report_fd, o->argv[0]);
}

/* The cgroup's count of out-of-memory kills, or -1 if it cannot be read (e.g. no cgroup v2). */
static long oom_kills(void) {
    FILE *events = fopen("/sys/fs/cgroup/memory.events", "r");
    if (events == NULL) return -1;
    char key[64];
    long value, kills = -1;
    while (fscanf(events, "%63s %ld", key, &value) == 2) {
        if (strcmp(key, "oom_kill") == 0) kills = value;
    }
    fclose(events);
    return kills;
}

static void json_string(const char *s) {
    putchar('"');
    for (; *s; s++) {
        unsigned char c = (unsigned char) *s;
        if (c == '"' || c == '\\') printf("\\%c", c);
        else if (c < 0x20) printf("\\u%04x", c);
        else putchar(c);
    }
    putchar('"');
}

static int run(const struct options *o) {
    int report[2];
    if (pipe2(report, O_CLOEXEC) != 0) {
        perror("oj-runner: pipe");
        return 1;
    }
    long kills_before = oom_kills();
    long start = now_ms();
    pid_t pid = fork();
    if (pid < 0) {
        perror("oj-runner: fork");
        return 1;
    }
    if (pid == 0) {
        close(report[0]);
        child(o, report[1]);
    }
    close(report[1]);

    int status = 0;
    struct rusage usage;
    memset(&usage, 0, sizeof usage);
    int wall_timed_out = 0;
    for (;;) {
        pid_t done = wait4(pid, &status, WNOHANG, &usage);
        if (done == pid) break;
        if (done < 0 && errno != EINTR) {
            perror("oj-runner: wait4");
            return 1;
        }
        if (o->wall_ms > 0 && now_ms() - start > o->wall_ms) {
            wall_timed_out = 1;
            kill(-pid, SIGKILL);
            kill(pid, SIGKILL);
            while (wait4(pid, &status, 0, &usage) < 0 && errno == EINTR) {}
            break;
        }
        struct timespec pause = {0, 2 * 1000 * 1000};
        nanosleep(&pause, NULL);
    }
    long wall = now_ms() - start;
    /* Anything the program left running in its group goes too. */
    kill(-pid, SIGKILL);

    char error[256] = {0};
    ssize_t n = read(report[0], error, sizeof error - 1);
    if (n < 0) n = 0;
    error[n] = '\0';
    close(report[0]);

    long cpu = usage.ru_utime.tv_sec * 1000L + usage.ru_utime.tv_usec / 1000L
            + usage.ru_stime.tv_sec * 1000L + usage.ru_stime.tv_usec / 1000L;
    int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : 0;
    int sig = WIFSIGNALED(status) ? WTERMSIG(status) : 0;
    long kills_after = oom_kills();
    int oom_killed = kills_before >= 0 && kills_after > kills_before;

    printf("{\"exitCode\":%d,\"signal\":%d,\"cpuMs\":%ld,\"wallMs\":%ld,\"maxRssKb\":%ld,\"wallTimedOut\":%s,"
           "\"oomKilled\":%s,\"error\":",
            exit_code, sig, cpu, wall, usage.ru_maxrss, wall_timed_out ? "true" : "false",
            oom_killed ? "true" : "false");
    if (n > 0) json_string(error);
    else fputs("null", stdout);
    puts("}");
    fflush(stdout);
    return 0;
}

static volatile sig_atomic_t stopping = 0;

static void on_stop(int sig) {
    (void) sig;
    stopping = 1;
}

static void on_child(int sig) {
    (void) sig;
}

/* PID 1 ignores signals it has no handler for, so SIGTERM must be handled for `docker stop` to be quick. */
static int idle(void) {
    struct sigaction stop = {0}, child_exit = {0};
    stop.sa_handler = on_stop;
    child_exit.sa_handler = on_child;
    sigaction(SIGTERM, &stop, NULL);
    sigaction(SIGINT, &stop, NULL);
    sigaction(SIGCHLD, &child_exit, NULL);
    while (!stopping) {
        pause();
        while (waitpid(-1, NULL, WNOHANG) > 0) {}
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc >= 2 && strcmp(argv[1], "idle") == 0) return idle();
    if (argc >= 2 && strcmp(argv[1], "run") == 0) {
        struct options o;
        parse(argc, argv, &o);
        return run(&o);
    }
    usage();
    return 2;
}
