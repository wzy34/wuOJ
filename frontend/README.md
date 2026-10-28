# wuOJ web app

The web app of wuOJ, a public online judge: browse problems, write C++ in the browser, register
to submit and watch the verdict arrive live; admins manage accounts.

Built with Vite, React 18, TypeScript and [Arco Design](https://arco.design/react). The API
contract lives in [`../api/openapi.yaml`](../api/openapi.yaml); the client code is generated from
it, and a mock mode serves it in the browser so the app runs without a backend.

## Prerequisites

- **Node.js 24** (LTS) or newer
- **pnpm 12** (`npm install -g pnpm`)

## Getting started

```bash
cd frontend
pnpm install
pnpm dev:mock        # http://localhost:5173, no backend needed
```

Browse as a visitor, register (accounts made in mock mode last until the page reloads), or sign in
with **`student` / `student`** (also `alice` / `alice`, `bob` / `bob`, and the admin `admin` / `admin`).

> If `pnpm install` fails with `No matching version found` while using a registry mirror (such as
> npmmirror), the mirror has not synced a new release yet. Retry with
> `pnpm install --registry=https://registry.npmjs.org`.

`pnpm-workspace.yaml` approves the install scripts of `esbuild` and `msw` (pnpm runs no dependency
scripts unless they are approved). MSW's script keeps `public/mockServiceWorker.js` in sync with the
installed version.

## Scripts

| Script                              | What it does                                                                  |
| ----------------------------------- | ----------------------------------------------------------------------------- |
| `pnpm dev`                          | Dev server; `/api` is proxied to the backend at `http://localhost:8080`       |
| `pnpm dev:mock`                     | Dev server with the API mocked in the browser                                 |
| `pnpm build` / `pnpm preview`       | Production build into `dist/` / serve it locally                              |
| `pnpm test`                         | Unit and component tests (Vitest + Testing Library, API mocked with MSW)      |
| `pnpm test:e2e`                     | End-to-end tests in Chromium; start their own mock-mode server on port 5180   |
| `pnpm test:e2e:backend`             | The same tests against a running backend (see `playwright.backend.config.ts`) |
| `pnpm lint` / `pnpm typecheck`      | ESLint / TypeScript checks                                                    |
| `pnpm format` / `pnpm format:check` | Prettier                                                                      |
| `pnpm lint:api`                     | Validate `../api/openapi.yaml`                                                |
| `pnpm gen:api`                      | Regenerate the API client from the contract                                   |
| `pnpm check:api`                    | Fail if the generated client is out of date with the contract                 |

Before the first `pnpm test:e2e`, install the browser once: `pnpm exec playwright install chromium`.

## Mock mode

`pnpm dev:mock` answers every contract endpoint in the browser, including the live event stream,
with demo data: 45 problems, a few seeded submissions, and a simulated judge that moves new
submissions through Pending → Compiling → Running → verdict. Mock data is kept in `localStorage`,
so it survives reloads; clear site data to start over.

The simulated judge decides the verdict from the code:

| Code contains                                                           | Verdict           |
| ----------------------------------------------------------------------- | ----------------- |
| `// @mock-verdict: WA` (or `TLE`, `MLE`, `RE`, `CE`, `OLE`, `SE`, `AC`) | That verdict      |
| no `main(`                                                              | Compilation Error |
| anything else                                                           | Accepted          |

Submitting more than 3 times within 10 seconds returns "Too many submissions", as the real API
will. Mock code is never part of a production build.

## Changing the API

The contract is shared with the backend, so change it first and generate from it:

1. Edit `../api/openapi.yaml`, then run `pnpm lint:api`.
2. Run `pnpm gen:api`. This rewrites `src/api/generated/`; never edit those files by hand.
3. Update the mocks in `src/mocks/` if behaviour changed, then `pnpm typecheck` and `pnpm test`.

CI should run `pnpm check:api` so a contract change cannot land without its regenerated client.

The live verdict stream (`GET /api/submissions/{id}/events`) is excluded from generation and read
with `EventSource` in `src/features/submissions/useSubmissionLive.ts`; its payload type,
`SubmissionStatusEvent`, is still generated.

## Project layout

```
src/
├── main.tsx            starts mock mode (dev only), then renders the app
├── app/                providers, routes, root layout (session expiry), error and 404 pages
├── layouts/            top bar and page frame
├── api/http.ts         axios instance: CSRF header, session-expiry signal, error helpers
├── api/generated/      orval output: types, request functions, TanStack Query hooks, MSW mocks
├── features/
│   ├── auth/           login page, session check, sign-out, redirect safety
│   ├── problems/       problem list, workspace (statement, editor, drafts, result panel)
│   └── submissions/    submissions list, detail page, live verdict hook
├── components/         editor, Markdown/KaTeX view, verdict tag, page states, copy button
├── lib/                query client, theme, formatting, Monaco setup, storage helpers
├── stores/             saved preferences (theme, editor font size)
├── mocks/              mock backend: fixtures, handlers, simulated judge
└── test/               test setup, polyfills, Monaco stub, render helpers
e2e/                    Playwright end-to-end tests
```

## Writing problem statements

Statements are Markdown (GitHub flavour) with KaTeX math:

- inline math: `$1 \le n \le 10^5$`
- display math: `$$` on its own line, the formula, then `$$` on its own line. `$$x$$` on a single
  line renders as inline math.
- Raw HTML is shown as text, never rendered. Links may only use `http`, `https` and `mailto`.

## Pinned versions

Some dependencies are deliberately held back; check these before upgrading:

| Package              | Pinned to | Why                                                                                                    |
| -------------------- | --------- | ------------------------------------------------------------------------------------------------------ |
| `react`, `react-dom` | 18.3      | Arco's popups rely on `findDOMNode`, which React 19 removed; React 19 also needs Arco's adapter import |
| `react-router`       | 7.x       | React Router 8 requires React ≥ 19.2.7                                                                 |
| `typescript`         | 6.0       | `typescript-eslint` does not support TypeScript 7 yet                                                  |
| `msw`                | 2.x       | orval's generated mocks import from `msw`; MSW 3 moved those exports to `msw/http`                     |

Two Monaco details are easy to break on upgrade:

- The base editor worker is imported as `monaco-editor/editor/editor.worker?worker`
  (`src/lib/monaco.ts`); older guides show a path that no longer resolves.
- `vite.config.ts` replaces Monaco's TypeScript/CSS/HTML/JSON language services with an empty
  module, because their workers would add about 9 MB to the build. After upgrading
  `monaco-editor`, run `pnpm build` and check that `dist/assets` contains only one worker,
  `editor.worker-*.js`.
