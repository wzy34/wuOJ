## 1. Project setup

- [x] 1.1 Scaffold `frontend/` as a Vite 8 + React 18.3 + TypeScript 6.0 (strict, `moduleResolution: bundler`) app with `engines.node >= 24` and `packageManager: pnpm@12.15.0`; verify `pnpm install`, `pnpm dev` and `pnpm build` succeed
- [x] 1.2 Add runtime dependencies at the versions in design.md (Arco 2.66, react-router 7.18, TanStack Query 5, axios, zustand 5, dayjs, @monaco-editor/react + monaco-editor, react-markdown + remark-gfm + remark-math + rehype-katex + katex); verify `pnpm ls --depth 0` shows react-router 7.x and react 18.3.x with no peer-dependency warnings
- [x] 1.3 Configure ESLint 10 flat config (typescript-eslint, react-hooks, react-refresh) and Prettier, and add `lint`, `typecheck` and `format` scripts; verify `pnpm lint` and `pnpm typecheck` pass on the scaffold
- [x] 1.4 Configure Vitest 5 with jsdom, jest-dom and a shared setup file, and add a trivial component test; verify `pnpm test` passes
- [x] 1.5 Configure the Vite dev server proxy `/api` → `http://localhost:8080` and a `dev:mock` script setting `VITE_API_MOCK=true`; verify with `curl -i localhost:5173/api/auth/me` that the request reaches the proxy target (connection-refused error from the proxy while no backend runs)

## 2. API contract and generated client

- [x] 2.1 Write `api/openapi.yaml` (OpenAPI 3.1) with the student-core endpoints, paging shape, `ApiProblem` errors, CSRF/session notes, enums, and the `SubmissionStatusEvent` schema as specified in `specs/api-contract`; verify it passes `npx @redocly/cli lint api/openapi.yaml` with no errors
- [x] 2.2 Implement `src/api/http.ts`: axios instance with explicit `XSRF-TOKEN`/`X-XSRF-TOKEN` names, the orval `customInstance` mutator, a 401 → session-expired event, and 429 `Retry-After` parsing; verify with unit tests for each interceptor path
- [x] 2.3 Add `orval.config.ts` (tags-split, react-query, axios mutator, msw mocks) and a `gen:api` script, then generate and commit `src/api/generated/`; verify `pnpm check:api` (regenerates and compares; the repo has no git yet) reports up to date and `pnpm typecheck` passes
- [x] 2.4 Set up the TanStack Query client (staleTime 30 s, no window-focus refetch, no retry on 4xx) with a global error notifier; verify a unit test showing that a 500 triggers one notification and a 404 is not retried

## 3. Mock mode

- [x] 3.1 Install MSW 2.15 (pinned `^2.15.0`) and faker, run `msw init public --save`, and add `src/mocks/browser.ts` / `node.ts` combining the generated handlers; verify `pnpm dev:mock` logs "[MSW] Mocking enabled" and generated endpoints answer
- [x] 3.2 Make `main.tsx` await `worker.start({ onUnhandledRequest: 'bypass' })` only when `import.meta.env.DEV && VITE_API_MOCK === 'true'`; verify `pnpm build` output contains no `mockServiceWorker`/msw code (grep `dist/`)
- [x] 3.3 Write the stateful auth mock (demo user `student`/`student`, session, 401 when signed out, CSRF header check) and problem fixtures (≥ 5 problems, mixed difficulty, KaTeX and samples, per-user SOLVED/ATTEMPTED status); verify with Vitest tests against `msw/node`
- [x] 3.4 Write the mock judge and submission handlers (create, list with scope/problem/verdict filters, detail with owner-only source, timed PENDING → COMPILING → RUNNING k/n → FINISHED, `// @mock-verdict:` markers, CE when `main` is missing, 429 after rapid submits) plus the `sse()` events handler; verify unit tests cover each verdict marker and the event sequence

## 4. App shell

- [x] 4.1 Build `App.tsx` providers: `ConfigProvider locale={enUS}`, Arco CSS import, QueryClientProvider, RouterProvider; verify a component test that a paginated Arco table renders English pagination labels
- [x] 4.2 Implement theme handling: preferences store (zustand persist), pre-paint script in `index.html`, system-scheme listener, `arco-theme` body attribute and body CSS variables; verify with tests for the system-follow and remembered-choice scenarios, and by reloading in dark mode without a flash
- [x] 4.3 Implement the router (`/login`, pathless RequireAuth + AppLayout, `/problems`, lazy `/problems/:slug`, `/submissions`, `/submissions/:id`, `/` → `/problems`, `*` → 404) with a route ErrorBoundary; verify routing tests for the redirect, the 404 page, a deep-link render and a thrown render error showing the "Reload" screen
- [x] 4.4 Build `AppLayout` (platform name, Problems/Submissions links with active state, theme toggle, user menu with Sign out) and a `usePageTitle` helper; verify component tests for the active link and the tab title
- [x] 4.5 Build shared `PageState` components (loading skeleton, empty state, error with Retry) and `VerdictTag` covering every status/verdict with label, colour, icon and full name on hover; verify a test rendering all eleven states

## 5. Authentication

- [x] 5.1 Build `LoginPage` with Arco Form (required fields, error message, keep username, clear password) and the `safeRedirect` helper (only single-slash in-app paths); verify tests for success, wrong password, empty fields and `redirect=https://evil.example`
- [x] 5.2 Implement `RequireAuth` (full-page spinner while `GET /auth/me` loads, redirect with remembered path), the signed-in redirect away from `/login`, and session-expiry handling (info message + redirect); verify tests for the deep-link-while-signed-out, reload-while-signed-in and expiry scenarios
- [x] 5.3 Implement sign-out (logout call → `queryClient.clear()` → `/login`); verify a test where user A signs out, user B signs in, and no cached data from A appears

## 6. Problem list

- [x] 6.1 Build `ProblemListPage`: Arco Table with number, title link, difficulty tag and Solved/Attempted icons (icon + text, not colour only), server-side pagination wired to the generated hook; verify component tests with mock data for rendering and title navigation
- [x] 6.2 Add debounced title search and difficulty filter synced to URL search params (resetting to page 1) and the empty state with "Clear search"; verify tests for search, no-match, and back-button restore of page/search/filter

## 7. Problem workspace

- [x] 7.1 Build `MarkdownView` (GFM, math, KaTeX CSS, http/https/mailto-only `urlTransform`, new-tab links, no raw HTML) and `Samples` with copy buttons; verify tests that `$1 \le n$` renders KaTeX markup, `<script>` is not executed or rendered as an element, and copy puts exact text on the clipboard
- [x] 7.2 Build `CodeEditor`: local Monaco via `loader.config({ monaco })` with an editor-only `MonacoEnvironment`, theme sync, persisted font size, Ctrl/Cmd+Enter command; verify in the browser with the network blocked to jsdelivr that the editor still loads, and with a unit test against the editor stub for the keybinding callback
- [x] 7.3 Implement drafts (`oj:draft:v1:<userId>:<problemId>:CPP17`, 500 ms debounce, safe storage, starter template, "Reset to template" with confirmation); verify tests for restore, per-user isolation, reset, and storage throwing
- [x] 7.4 Assemble `WorkspacePage` with `ResizeBox.Split` (min widths, stacked below 768 px), statement header with limits, "Problem not found" state and page title; verify tests for not-found and the narrow-width layout
- [x] 7.5 Implement submitting (disabled when blank or in flight, 64 KB client limit, 429 message with seconds) and the result panel; verify tests for success → Pending within 1 s, double click → one request, oversized code, and rate limiting
- [x] 7.6 Implement `useSubmissionLive` (EventSource, `setQueryData` on generated keys, close on final, invalidate problem list and my-submissions, error → 2 s polling via `refetchInterval`) and show progress, final verdict, time/memory, CE compiler output and a detail link; verify tests driving the MSW `sse()` handler for AC, CE and a dropped stream that still reaches the final verdict
- [x] 7.7 Add the "My submissions" tab for the current problem (newest first, links to detail); verify a test with three mock submissions

## 8. Submission history

- [x] 8.1 Build `SubmissionListPage` (columns per spec, mine/all switch defaulting to mine, problem and verdict filters, URL-synced page/filters, 5 s refresh while any row is unfinished); verify tests for the default view, verdict filter, a shared-link URL, and a running row turning Accepted
- [x] 8.2 Build `SubmissionDetailPage` (metadata, read-only highlighted source with copy for the owner, "only visible to its author" for others, per-test table or compiler output, live updates while unfinished, "Submission not found"); verify tests for the owner-WA, non-owner and not-found scenarios

## 9. End-to-end verification and docs

- [x] 9.1 Configure Playwright with a `webServer` running `dev:mock` and write the smoke test: sign in → open a problem → submit with `// @mock-verdict: WA` → see "Wrong Answer" live → open detail and see the failed test; verify `pnpm test:e2e` passes
- [x] 9.2 Check the full flow manually in light and dark themes at 1440 px and 900 px widths in mock mode, and confirm `pnpm lint`, `pnpm typecheck`, `pnpm test` and `pnpm build` all pass
- [x] 9.3 Write `frontend/README.md` covering prerequisites (Node 24, pnpm), scripts, mock mode and verdict markers, regenerating the client after contract changes, and the pinned-version notes (React 18, React Router 7, TypeScript 6, MSW 2); verify a fresh clone can follow it to a running app
