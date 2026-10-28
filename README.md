# wuOJ

A public online judge: anyone can read problems and write C++17 in the browser; registered users submit and watch their
verdict arrive live, and admins manage accounts.

| Directory               | What it is                                                                 |
| ----------------------- | -------------------------------------------------------------------------- |
| [`api/`](api)           | The OpenAPI contract between the web app and the server                    |
| [`frontend/`](frontend) | The web app (React, Vite); runs on its own with in-browser mocks           |
| [`backend/`](backend)   | The server (Spring Boot, PostgreSQL, Redis, RabbitMQ) and the Docker judge |
| [`judge/`](judge)       | The judge container image and the runner that measures submissions         |
| [`dev-data/`](dev-data) | Sample problems with real tests, reference solutions and demo accounts     |
| [`openspec/`](openspec) | Specifications and design records                                          |

To try the web app alone, see [`frontend/README.md`](frontend/README.md). To run the whole platform locally:

```bash
cd backend
./gradlew buildJudgeImage && ./gradlew bootRun    # needs Docker
./gradlew importDevData                           # in another terminal
cd ../frontend && pnpm install && pnpm dev        # http://localhost:5173: browse, register, or sign in as student / student
```

Details are in [`backend/README.md`](backend/README.md).
