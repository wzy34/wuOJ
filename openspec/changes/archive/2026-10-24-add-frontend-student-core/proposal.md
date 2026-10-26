## Why

The online judge has an agreed architecture (Spring Boot API, go-judge workers, PostgreSQL/Redis/MinIO) but no code yet. The student-facing frontend is the first slice: it lets students sign in, read problems, write C++ in the browser, submit, and watch the verdict arrive — the core loop of the platform for a classroom or club. Building it contract-first against a shared OpenAPI file lets frontend work start now, before the backend exists, and gives the future backend a precise contract to implement.

## What Changes

- Add a new single-page web app in `frontend/` built with Vite, React 18.3, TypeScript and Arco Design React.
- Add `api/openapi.yaml` at the repo root: the contract for the student-core endpoints (session auth, problems, submissions) plus the schema of live verdict events.
- Generate the typed axios client, TanStack Query hooks and MSW mock handlers from that contract with orval; add a mock mode so the whole app runs without a backend.
- Pages:
  - **Login** (username + password; no self-registration).
  - **Problem list** with search, pagination and per-user solved status.
  - **Problem workspace**: Markdown + KaTeX statement with copyable samples, Monaco C++17 editor with per-problem local drafts, Submit, and a live verdict panel (SSE with polling fallback).
  - **Submissions list** (mine / all, filters) and **submission detail** (source, per-test results, compiler output).
- App shell: top navigation, light/dark theme (follows the system by default), desktop-first layout, 404 and error pages, English UI.
- Testing: unit/component tests with Vitest and Testing Library; one Playwright end-to-end smoke test running against the mocks.
- Out of scope for this change: admin tools, contests and scoreboard, registration, custom-input "Run", languages other than C++17, i18n, and any backend/judge implementation.

## Capabilities

### New Capabilities
- `api-contract`: The OpenAPI contract for student-core endpoints and live verdict events, the generated client/hooks, and mock mode that serves the contract without a backend.
- `web-app-shell`: App bootstrap, routing, top navigation, theme switching, layout breakpoints, 404 and error handling.
- `web-auth`: Login, session restore on load, logout, and redirecting signed-out users away from protected pages.
- `problem-browsing`: The problem list — search, pagination, difficulty and solved status.
- `problem-workspace`: Problem statement rendering, the C++ editor with local drafts, submitting, and live verdict display.
- `submission-history`: Submissions list with filters and the submission detail view.

### Modified Capabilities
<!-- None: there are no existing specs in openspec/specs/. -->

## Impact

- **New code**: `frontend/` (the whole app) and `api/openapi.yaml`. No existing code is affected — the repository currently contains only OpenSpec scaffolding.
- **Future backend**: the Spring Boot API must implement `api/openapi.yaml` as written, including cookie sessions with Spring Security's `XSRF-TOKEN` CSRF cookie, Spring's `ProblemDetail` (RFC 9457) error bodies, and the SSE endpoint for submission events.
- **Dependencies (frontend)**: React 18.3, Arco Design React 2.66, React Router 7, TanStack Query 5, axios, zustand, Monaco (`@monaco-editor/react` + `monaco-editor`), react-markdown with remark-gfm/remark-math/rehype-katex, dayjs, orval, MSW 2, faker, Vite 8, TypeScript 6.0, Vitest, Testing Library, Playwright, ESLint with typescript-eslint.
- **Tooling**: Node.js 24 LTS and pnpm (both installed on the development machine).
