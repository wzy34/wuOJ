## Context

The repository is greenfield: only OpenSpec scaffolding exists. The agreed platform architecture is a Spring Boot 4 API (session cookies via Spring Security, Spring Session on Redis, `ProblemDetail` errors, SSE for live updates), go-judge workers, PostgreSQL, Redis and MinIO — none of which exist yet. This change builds only the student-facing web app plus the API contract it consumes (see proposal.md and the six capability specs for scope and required behavior).

Constraints that shape the design:
- **Arco Design React** is the chosen component library. It is a client-side library whose default locale is Chinese (`es/locale/default.js` re-exports `zh-CN`).
- **Classroom networks** may block public CDNs, so nothing at runtime may load from a CDN.
- **No backend yet**, so the app must be fully usable against mocks, and the mocks must match what the backend will later implement.
- Development machine: Node.js 24.22 LTS, pnpm 12.15.

Library versions and APIs below were checked on 2026-10-24 against the npm registry (versions, peer dependencies, engines), Context7 documentation (Arco, React Router, TanStack Query, orval, MSW, monaco-react, react-markdown, Vite) and, where docs were silent, the published package source.

## Goals / Non-Goals

**Goals:**
- One contract file drives types, request code, query hooks and mocks, so the frontend and the future backend cannot silently drift.
- The full student flow (sign in → browse → code → submit → live verdict → history) works with `pnpm dev` and no backend.
- Heavy dependencies (Monaco, KaTeX) load only on the pages that need them.
- Pinned, mutually compatible versions with a clear upgrade path.

**Non-Goals:**
- Server-side rendering or SEO (every page is behind a login).
- Custom Arco theming beyond light/dark (no Less build, no Design Lab theme package yet).
- Offline/PWA support, i18n, accessibility audit beyond semantic Arco usage and non-colour status cues.
- Any backend, judge or deployment work; contract conformance testing of the backend comes with the backend change.

## Decisions

### 1. Vite single-page app, not Next.js
All pages are authenticated, so SSR buys nothing, and Arco's global CSS plus imperative `Message`/`Modal` APIs fit a client-only app. The build is static files that any web server can host, with `/api` reverse-proxied to Spring on the same origin (no CORS, cookies and CSRF just work).
*Alternatives:* Next.js App Router (would need `'use client'` nearly everywhere); Rsbuild (fine, but Vite has the larger ecosystem and the docs we verified).

### 2. React 18.3, React Router 7 — not React 19 / React Router 8
- Arco supports React 19 only through an adapter import (`@arco-design/web-react/es/_util/react-19-adapter`), and its DOM lookup for `Trigger`-based components (Tooltip, Popover, Select, Dropdown) falls back to `findDOMNode`, which React 19 removed; on 18.3 that path still works.
- React Router 8.x requires `react >= 19.2.7`, so with React 18.3 we use **React Router 7.18.x** (peer `react >= 18`, last release 2026-09-15).
- *Upgrade path (later change):* React 19 + the Arco adapter import + React Router 8, after checking Arco popups around custom components.

### 3. TypeScript 6.0, not 7.0
`typescript-eslint` 8.72 supports `typescript >= 4.8.4 < 6.1.0`. TypeScript 7.0 (released 2026-07) would break linting. MSW 2 needs `>= 4.8`. Use `typescript ~6.0.3` with `moduleResolution: "bundler"` and `strict: true`.

### 4. Arco styling: precompiled CSS, no Less, no Vite plugin
- Import `@arco-design/web-react/dist/css/arco.css` once; components tree-shake by default (verified in Arco's Quick Start).
- Skip `@arco-plugins/vite-react` and Less: on-demand styles and Less `modifyVars` theming aren't needed yet, and fewer build plugins means less risk on Vite 8.
- Wrap the app in `<ConfigProvider locale={enUS}>` (`@arco-design/web-react/es/locale/en-US`). Without it, pagination, empty states and pickers render in Chinese.
- Dark mode is Arco's documented mechanism: `document.body.setAttribute('arco-theme', 'dark')` / `removeAttribute`, with `body { background-color: var(--color-bg-1); color: var(--color-text-1); }`. Brand colours can later be overridden through Arco's CSS variables without introducing Less.

### 5. Contract-first with orval
- `api/openapi.yaml` (OpenAPI 3.1) at the repo root is hand-written and owned jointly by frontend and backend. Paths are written in full (`/api/problems`, …) so that the generated client, the mocks and the dev proxy all agree without base-URL juggling.
- Shared schemas: `CurrentUser`, `ProblemSummary` (with `status: SOLVED | ATTEMPTED | NONE`), `Problem` (statement Markdown, limits, `samples[]`, `languages[]`), `SubmissionSummary`, `SubmissionDetail` (optional `sourceCode` / `compileOutput`, `testResults[]`), `SubmissionStatusEvent`, `Page*` wrappers, and `ApiProblem` for RFC 9457 errors (named to avoid clashing with the `Problem` domain schema). Enums: `Difficulty`, `Language` (`CPP17`), `SubmissionStatus`, `Verdict`.
- **orval 8.44** config (`frontend/orval.config.ts`): input `../api/openapi.yaml`; output `src/api/generated/` with `mode: 'tags-split'`, `client: 'react-query'`, `httpClient: 'axios'`, a custom mutator (`src/api/http.ts` → `customInstance`), and `mock: true` (MSW handlers with faker data). Generated files are committed; CI re-runs generation and fails on any diff.
- SSE isn't generated by orval. The `SubmissionStatusEvent` schema is still defined in the contract (so its TS type is generated), and the stream is consumed by a small hand-written hook (Decision 9).
- *Alternatives:* openapi-typescript + openapi-fetch (lighter, but no generated Query hooks or MSW mocks); code-first from springdoc (impossible until the backend exists).

### 6. MSW 2.15 (pinned below 3) for mock mode and tests
- orval 8.44's mock generator emits `import { http, HttpResponse, delay } from 'msw'` (checked in `@orval/mock` source). MSW 3.0 moved those exports to `msw/http`, so generated mocks would not compile. **Pin `msw@^2.15.0`** until orval emits MSW-3 imports. MSW 2.15 also exports `sse()` for mocking Server-Sent Events.
- Browser setup follows the MSW docs: run `msw init public --save`; `src/mocks/browser.ts` calls `setupWorker(...handlers)`; `main.tsx` awaits `worker.start({ onUnhandledRequest: 'bypass' })` before rendering, but only when `import.meta.env.DEV && import.meta.env.VITE_API_MOCK === 'true'`. Because the worker is loaded by a dynamic `import()` behind a compile-time-false condition, production builds contain no mock code.
- Handlers are layered:
  - **Generated** (faker) handlers are the fallback.
  - **Hand-written stateful** handlers override them for auth (in-memory session; demo user `student` / `student`), problems (fixed fixture set: at least 5 problems with KaTeX and samples), and submissions:
    - A **mock judge** advances a submission on timers: `PENDING` → `COMPILING` → `RUNNING` (test 1..n) → `FINISHED`.
    - The verdict is chosen by a marker comment in the code, `// @mock-verdict: WA|TLE|MLE|RE|CE|OLE|SE`. With no marker: code without `main` gets CE, anything else gets AC.
    - The events endpoint is served with `sse()`.
- The same handler set is reused in Vitest via `setupServer` from `msw/node`.

### 7. Server state: TanStack Query 5 via generated hooks
- Query client defaults: `staleTime: 30s`, `refetchOnWindowFocus: false`, and no retry on 4xx (one retry otherwise).
- A global `QueryCache`/`MutationCache` `onError` shows `Message.error` for network and 5xx errors. Pages render an inline retry for missing data.
- Feature code calls the generated hooks (e.g. `useListProblems`, `useGetSubmission`) and the generated query-key helpers, so cache updates from SSE target exactly the keys the hooks use.

### 8. HTTP layer and auth
- `src/api/http.ts` creates an axios instance (`withCredentials` not needed: same origin). axios's default `xsrfCookieName: 'XSRF-TOKEN'` / `xsrfHeaderName: 'X-XSRF-TOKEN'` already match Spring Security's cookie CSRF repository; the defaults are set explicitly for clarity.
- A response interceptor maps 401 to an app-level "session expired" event, which the router turns into a redirect to `/login?redirect=<current path>` with an info message. 429 is surfaced with the `Retry-After` seconds.
- Auth state is the `GET /api/auth/me` query and nothing else: no duplicate copy in a store.
  - A `RequireAuth` layout route waits for it, showing a full-page spinner.
  - Sign-out calls logout, then `queryClient.clear()`, then navigates to `/login`.
  - The `redirect` parameter is followed only if it starts with a single `/`; `//` and absolute URLs are rejected.

### 9. Live verdict: native EventSource with a polling fallback
- `useSubmissionLive(id)` opens `new EventSource('/api/submissions/{id}/events')`. Cookies go automatically on same-origin requests, so no custom headers are needed.
- Each `status` event is merged into the detail query with `queryClient.setQueryData(getGetSubmissionQueryKey(id), …)`.
- On a final event the hook closes the stream and invalidates the problem list and "my submissions" queries, so Solved status updates.
- On `error` the hook closes the EventSource (preventing the browser's endless auto-reconnect) and enables polling. The detail query's `refetchInterval` callback (TanStack v5 receives only `query`) returns `2000` while not finished and `false` once finished.
- The submissions list uses the same callback style: `4000` while any row on the page is unfinished (4 s rather than 5 s so request time still keeps updates within the spec's 5-second bound).
- *Alternatives:* WebSocket/STOMP (bidirectional, not needed here); `@microsoft/fetch-event-source` (only needed for custom headers).

### 10. Routing: React Router 7 data mode
- `createBrowserRouter` + `RouterProvider` (from `react-router/dom`), as documented.
- Route tree:
  - `/login` is public.
  - A pathless `RequireAuth` route wraps a pathless `AppLayout` (top nav + `<Outlet/>`). Under it: `/problems`, `/problems/:slug`, `/submissions`, `/submissions/:id`; `/` redirects to `/problems`; `*` shows the 404 page.
- The workspace route uses `lazy` so Monaco and KaTeX land in their own chunk.
- A route-level `ErrorBoundary` provides the crash screen while the top bar stays mounted.
- Route loaders aren't used: data fetching stays in TanStack Query. List state lives in `useSearchParams`.

### 11. Code editor: Monaco bundled locally
- `@monaco-editor/react` 4.7 + `monaco-editor` 0.57, following monaco-react's documented Vite recipe: `loader.config({ monaco })` plus `self.MonacoEnvironment.getWorker`.
- C++ has no language worker, so `getWorker` always returns the base editor worker. In monaco-editor 0.57 its import is `monaco-editor/editor/editor.worker?worker` (the package now maps `monaco-editor/*` to `esm/vs/*.js`); the `monaco-editor/esm/vs/editor/editor.worker` path in the monaco-react README no longer resolves. The TS/JSON/CSS/HTML workers aren't bundled. This meets the "works without CDNs" requirement and keeps the chunk smaller.
- The full `monaco-editor` entry also registers the TypeScript/CSS/HTML/JSON language services, whose workers Vite would emit (~9 MB). `vite.config.ts` aliases those four `languages/features/*/register.js` modules to an empty module; C++ highlighting comes from the separate, lazily loaded language definitions and is unaffected.
- Every editor goes through `LazyCodeEditor` (`React.lazy`), so Monaco (~4 MB) downloads only on pages that show an editor. Without this, the submission detail page (part of the main bundle) pulled Monaco into every page.
- The editor theme follows the app theme (`vs` / `vs-dark`). Ctrl/Cmd+Enter is registered with `editor.addCommand(KeyMod.CtrlCmd | KeyCode.Enter, ...)` and submits `editor.getValue()`, because the keystroke can arrive before React has re-rendered with the last typed characters.
- Font size is a persisted preference.

### 12. Statements: react-markdown without raw HTML
- `react-markdown` 10 with `remark-gfm`, `remark-math` and `rehype-katex`, importing `katex/dist/katex.min.css` (rehype-katex does not include the CSS).
- react-markdown never turns raw HTML into elements unless `rehype-raw` is added, so we **don't add `rehype-raw` or `rehype-sanitize`**. Sanitize would be redundant, and placed after KaTeX it would strip KaTeX's markup.
- A custom `urlTransform` allows only `http`, `https` and `mailto` (the default also permits irc/xmpp). A custom `a` renderer adds `target="_blank" rel="noopener noreferrer"`.
- Samples come from the structured `samples` field, not from the Markdown, so copy buttons copy exact text (Arco `Typography` `copyable`).

### 13. Client state and drafts
- **zustand 5** with `persist` holds UI preferences only: theme choice and editor font size.
- A tiny inline script at the top of `<body>` in `index.html` reads the stored theme (or `prefers-color-scheme`) and sets `arco-theme` before React mounts, so there's no flash of the wrong theme.
- **Drafts** go in `localStorage` under `oj:draft:v1:<userId>:<problemId>:CPP17`:
  - written 500 ms after typing stops;
  - every read and write wrapped in try/catch so blocked storage degrades silently;
  - sign-out does not delete them, but they are only ever shown to the same user id.

### 14. Project layout
```
api/openapi.yaml                    # the contract (repo root, shared with backend)
frontend/
├── index.html                      # pre-paint theme script
├── vite.config.ts                  # react plugin, /api proxy → http://localhost:8080
├── orval.config.ts
├── playwright.config.ts
├── public/mockServiceWorker.js     # generated by `msw init`
└── src/
    ├── main.tsx                    # enable mocks (dev only) → render <App/>
    ├── app/                        # App.tsx (providers), router.tsx, RequireAuth, ErrorBoundary
    ├── layouts/AppLayout.tsx       # top nav, theme toggle, user menu
    ├── api/
    │   ├── http.ts                 # axios instance, interceptors, orval mutator
    │   └── generated/              # orval output — never edited by hand
    ├── features/
    │   ├── auth/                   # LoginPage, useCurrentUser, safeRedirect
    │   ├── problems/               # ProblemListPage, WorkspacePage, Statement, Samples, ResultPanel
    │   └── submissions/            # SubmissionListPage, SubmissionDetailPage, useSubmissionLive
    ├── components/                 # CodeEditor, MarkdownView, VerdictTag, PageState (loading/empty/error)
    ├── lib/                        # queryClient, theme, storage (safe localStorage), monaco setup
    ├── stores/preferences.ts
    ├── mocks/                      # browser.ts, node.ts, handlers/, fixtures/, mockJudge.ts
    └── test/                       # vitest setup (jest-dom, msw server)
```

### 15. Tooling and tests
- **Build:** Vite 8.3 (Rolldown-based) + `@vitejs/plugin-react` 6.1 (Oxc refresh, no Babel). `engines.node >= 24`, `packageManager: pnpm@12.15.0`.
- **Unit and component tests:** Vitest 5 + jsdom 30 + Testing Library (react 16, user-event 14, jest-dom 7), sharing the MSW handlers through `msw/node`. Monaco is replaced by a textarea stub in unit tests because jsdom can't run it.
- **End-to-end:** one Playwright 1.64 smoke test that starts `vite` in mock mode via `webServer`: sign in → open a problem → submit with `// @mock-verdict: WA` → see Wrong Answer → open the detail page.
- **Lint and format:** ESLint 10 flat config + `@eslint/js` 10 + typescript-eslint 8.72 + `eslint-plugin-react-hooks` 7 + `eslint-plugin-react-refresh`; Prettier 3.
- **Scripts:** `dev`, `dev:mock`, `gen:api`, `build`, `preview`, `test`, `test:e2e`, `lint`, `typecheck`.

### Verified versions (2026-10-24)

| Package | Version | Note |
|---|---|---|
| react / react-dom | 18.3.1 | `@types/react` 18.3.x |
| @arco-design/web-react | 2.66.18 | peer `react >= 16` |
| react-router | 7.18.x | 8.x needs React ≥ 19.2.7 |
| @tanstack/react-query | 5.104.x | peer React 18 or 19 |
| axios | 1.20.x | |
| zustand | 5.0.x | |
| @monaco-editor/react / monaco-editor | 4.7.0 / 0.57.x | |
| react-markdown, remark-gfm, remark-math, rehype-katex, katex | 10.1, 4.0, 6.0, 7.0, 0.19 | |
| dayjs | 1.11.x | |
| orval | 8.44.x | needs Node ≥ 22.18 |
| msw | 2.15.x | **not 3.x** (Decision 6) |
| @faker-js/faker | 10.x | orval mock peer `>= 8` |
| vite / @vitejs/plugin-react | 8.3.x / 6.1.x | |
| typescript | 6.0.x | **not 7.x** (Decision 3) |
| vitest / jsdom | 5.0.x / 30.x | |
| @playwright/test | 1.64.x | |
| eslint / typescript-eslint | 10.x / 8.72.x | |

## Risks / Trade-offs

- [React 18 is one major behind] → Kept deliberately for Arco stability; Decision 2 lists the upgrade steps. Arco may log `findDOMNode` deprecation warnings under `StrictMode` in development. They're harmless.
- [MSW pinned to 2.x] → Revisit when orval generates `msw/http` imports. The hand-written handlers use the same root imports, so both move together.
- [Hand-written contract can diverge from the Spring implementation] → The backend change must add a conformance check (e.g. diff springdoc's generated spec against `api/openapi.yaml`, or contract tests). Until then the contract file is the source of truth.
- [SSE buffered or cut by reverse proxies] → Deployment must disable proxy buffering for `/api/submissions/*/events` (backend/deploy change). The 2-second polling fallback keeps verdicts arriving regardless.
- [Monaco chunk is large (~4 MB, ~1 MB gzipped)] → Loaded only where an editor is shown, with no language-service workers. If it's still too heavy, switch to importing `editor.api` plus the needed contributions only.
- [The language-service alias depends on Monaco's internal file layout] → After upgrading `monaco-editor`, check that `dist/assets` contains only `editor.worker-*.js`; if `ts.worker`/`css.worker` reappear, update the alias pattern.
- [Drafts readable by others on shared lab computers] → Drafts aren't secrets and are only shown to the matching user id. Documented. Sign-out keeps drafts so students don't lose work.
- [Vite 8 / Rolldown is recent] → No exotic plugins are used. If a blocker appears, drop to Vite 7 with `@vitejs/plugin-react` 5 (supported on both).
- [Arco defaults to Chinese] → `ConfigProvider locale={enUS}` at the root, plus a component test that asserts English pagination text.

## Migration Plan

Greenfield: nothing to migrate or roll back. The app is developed and demoed in mock mode. When the backend exists, `pnpm dev` proxies `/api` to `http://localhost:8080`, and production serves `frontend/dist` behind the same reverse proxy as the API.

## Open Questions

- Logo for the top bar. The platform name is decided: **wuOJ** (a single constant used in the top bar and tab titles).
- Exact wording of the C++17 starter template (default: `#include <bits/stdc++.h>`, fast I/O, empty `main`).
- How the backend numbers problems for display (sequential id or a separate `displayId`). The contract will carry a `displayId` string either way.
