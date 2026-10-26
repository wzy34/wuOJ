# wuOJ backend

The server side of wuOJ: it implements the API contract in [`../api/openapi.yaml`](../api/openapi.yaml) and judges
C++17 submissions in Docker containers.

- **Spring Boot 4.1** (Java 25, Spring MVC on virtual threads) serves the API, with cookie sessions in **Redis** and
  SPA-style CSRF.
- **PostgreSQL 18** is the system of record (Flyway migrations, plain SQL through `JdbcClient`).
- **RabbitMQ 4** carries judge jobs to workers and live status events back to the API, which streams them to browsers
  with Server-Sent Events.
- **Docker** runs every submission in a locked-down container built from [`../judge/cpp`](../judge/cpp): no network,
  read-only root, unprivileged user, CPU/memory/process/output limits. A small C runner measures each test.

One application has two roles, set with `oj.roles`: `api` (HTTP, streams) and `worker` (queue consumer, sandbox).
Both run in one process by default. See
[`../openspec/changes/add-backend-student-core/design.md`](../openspec/changes/add-backend-student-core/design.md)
for the reasoning behind each decision.

## Prerequisites

- **Docker** with Compose v2: Docker Desktop, OrbStack or Colima. The backend finds the daemon through
  `oj.judge.docker-host`, then `DOCKER_HOST`, then Docker Desktop's `~/.docker/run/docker.sock`, then
  `/var/run/docker.sock`. With Colima, export `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`.
- **A JDK 17 or newer to run Gradle.** The build compiles with Java 25, which Gradle downloads automatically if it is
  not installed.
- The `docker` command on `PATH` for `bootRun` (Spring Boot starts `compose.yaml` with it). Docker Desktop installs it
  in `~/.docker/bin`.

## Getting started

```bash
cd backend
./gradlew buildJudgeImage     # the C++ judge image, wuoj/judge-cpp:dev (rebuild after changing judge/)
./gradlew bootRun             # starts PostgreSQL, Redis and RabbitMQ from compose.yaml, then the app on :8080
./gradlew importDevData       # in another terminal: demo users and the six sample problems from ../dev-data
```

Then run the web app against it with `cd ../frontend && pnpm dev` and open http://localhost:5173. Sign in as
**`student` / `student`** (also `alice`, `bob`, and `admin` / `admin` who can see hidden problems).

`curl localhost:8080/actuator/health` shows the database, Redis, RabbitMQ and Docker (DOWN until the judge image is
built). The RabbitMQ management UI is at http://localhost:15672 (`wuoj` / `wuoj`).

### Simulated judge

`./gradlew bootRun --args='--oj.judge.simulated=true'` judges without Docker, like the web app's mock mode: a
`// @mock-verdict: WA` comment (or TLE, MLE, RE, CE, OLE, SE) forces that verdict on test 3, code without `main(` is a
compilation error, and anything else is accepted. It is off by default and logs a warning when on. Never enable it in
production.

## Tests

| Command                 | What it runs                                                                                    |
| ----------------------- | ----------------------------------------------------------------------------------------------- |
| `./gradlew test`        | Unit and integration tests (Testcontainers: PostgreSQL, Redis, RabbitMQ; the simulated judge)   |
| `./gradlew sandboxTest` | Real compiling and judging in Docker: every verdict, the isolation guarantees, timing stability |
| `./gradlew check`       | Both                                                                                            |

Every request and response in the API tests is validated against `../api/openapi.yaml`, undocumented fields
included, and a final check fails if any operation of the contract was never exercised. `sandboxTest` builds the
judge image first.

End to end with the web app: start the backend with the dev data and the simulated judge, then
`cd ../frontend && pnpm test:e2e:backend`.

## Importing content

Problems and accounts are managed with files; there is no admin UI yet. Gradle runs the importers against the
development database (`importDevData`); against any database, run the jar with the usual `spring.datasource.*`
settings:

```bash
java -jar build/libs/wuoj-backend-0.1.0.jar import-users users.csv
java -jar build/libs/wuoj-backend-0.1.0.jar import-problems problems/      # a package, or a directory of packages
```

Both validate everything first and change nothing if anything is wrong, printing every problem found; the exit status
is non-zero on failure.

### Problem packages

```
a-plus-b/
├── problem.yaml      # displayId, slug, title, difficulty (EASY|MEDIUM|HARD), timeLimitMs (100-10000),
│                     # memoryLimitMb (16-1024), visible (default false)
├── statement.md      # Markdown with $math$ and $$display math$$
├── samples/1.in, 1.out, ...   # optional, shown on the problem page
└── tests/1.in, 1.out, ...     # hidden; at least one; at most 64 MB per file
```

Importing a slug that exists updates that problem. Changed tests become a new test-data version for new submissions;
earlier submissions keep their verdicts. Importing the same package twice changes nothing. See
[`../dev-data/problems`](../dev-data/problems) for examples and [`../dev-data/generate.py`](../dev-data/generate.py)
for how their tests are made.

### Users

```csv
username,password,role,enabled
carol,her-password,STUDENT,true
teacher,another-password,ADMIN
```

`enabled` is optional (default `true`). Existing usernames (matched ignoring case) are updated, which is how passwords
are reset. Passwords are stored as bcrypt hashes. A disabled account cannot sign in; its open sessions end at the idle
timeout.

## Configuration

Set in `application.yaml`, or as environment variables (`OJ_JUDGE_SLOTS=4,5,6`) or `--` arguments.

| Property                                     | Default              | Meaning                                                                                          |
| -------------------------------------------- | -------------------- | ------------------------------------------------------------------------------------------------ |
| `oj.roles`                                   | `api,worker`         | Which parts this process runs                                                                    |
| `oj.judge.image`                             | `wuoj/judge-cpp:dev` | Judge image                                                                                      |
| `oj.judge.slots`                             | one unpinned slot    | Concurrent judges, one cpuset each (e.g. `4,5,6`); several slots must be pinned to separate CPUs |
| `oj.judge.runtime`                           | Docker's default     | Container runtime for judge containers, e.g. `runsc` (gVisor)                                    |
| `oj.judge.docker-host`                       | auto-detected        | Docker endpoint                                                                                  |
| `oj.judge.work-dir`                          | `./var/judge`        | Per-submission files; must be the same path on the Docker host                                   |
| `oj.testdata.root`                           | `./var/testdata`     | Hidden tests, by problem and version                                                             |
| `oj.judge.simulated`                         | `false`              | The simulated judge (development only)                                                           |
| `oj.rate-limit.submissions` / `.window`      | `3` / `10s`          | Submissions per user per sliding window                                                          |
| `oj.rate-limit.unfinished`                   | `2`                  | Submissions a user may have waiting or running                                                   |
| `oj.login-throttle.max-failures` / `.window` | `5` / `5m`           | Failed sign-ins per username before 429                                                          |
| `spring.session.timeout`                     | `8h`                 | Session idle timeout                                                                             |

If the worker itself runs in a container, mount `oj.judge.work-dir` and `oj.testdata.root` at the same paths as on
the host: judge containers are started by the host's Docker daemon, which resolves bind mounts on the host.

## Layout

```
src/main/java/dev/wuoj/
├── auth/        sign-in, sessions, CSRF, throttling
├── problem/     problem list and detail
├── submission/  submitting, listing, live status streams
├── judge/       queue, claims and leases, recovery, the simulated judge
├── sandbox/     Docker containers, the runner protocol, verdict rules, output checker
├── testdata/    hidden-test storage
├── importer/    import-problems and import-users
└── common/      configuration, errors, paging, rate limiting
src/main/resources/db/migration/   Flyway migrations
```
