# api-contract Specification

## Purpose

Defines the HTTP contract between the web app and the judge backend for the student core, so both sides can be built independently, and a mock mode that serves that contract in the browser while no backend exists.

## Requirements

### Requirement: Single source contract
The repository SHALL contain one OpenAPI 3.1 document at `api/openapi.yaml` that describes every endpoint, request body, response body, error shape and live event payload used by the student core. Client-side API types and request functions SHALL be generated from this document rather than written by hand.

#### Scenario: Contract change propagates to the client
- **WHEN** a field is added to a response schema in `api/openapi.yaml` and client generation is re-run
- **THEN** the generated client types include the new field without any hand edits

#### Scenario: Generated code is not edited by hand
- **WHEN** a developer runs client generation on an unchanged contract
- **THEN** the generated files are identical to the committed ones

### Requirement: Student-core endpoints
The contract SHALL define, under the `/api` prefix:
- `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` for session handling;
- `GET /problems` (paged, with title search and difficulty filter) and `GET /problems/{slug}`;
- `POST /submissions`, `GET /submissions` (paged, filterable by scope `mine`/`all`, problem and verdict) and `GET /submissions/{id}`;
- `GET /submissions/{id}/events` as a `text/event-stream` of submission status events.

#### Scenario: Endpoint inventory
- **WHEN** the contract is inspected
- **THEN** each endpoint listed above is present with its parameters, success response and error responses documented

### Requirement: Paging shape
Every paged list endpoint SHALL accept `page` (1-based, default 1) and `size` (default 20, maximum 100) query parameters and SHALL return an object with `items`, `page`, `size`, `totalItems` and `totalPages`.

#### Scenario: Requesting the second page
- **WHEN** a client requests `GET /api/problems?page=2&size=20` and 45 problems match
- **THEN** the response contains problems 21–40 with `page` 2, `size` 20, `totalItems` 45 and `totalPages` 3

#### Scenario: Oversized page request
- **WHEN** a client requests `size=500`
- **THEN** the response is a 400 error

### Requirement: Error format
Every error response SHALL use the RFC 9457 problem details format (`application/problem+json`) with at least `status`, `title` and `detail`; validation errors SHALL additionally list the offending fields with a message for each.

#### Scenario: Invalid submission body
- **WHEN** a client posts a submission with empty source code
- **THEN** the response is 400 with a problem-details body whose field errors include `sourceCode`

### Requirement: Session and CSRF conventions
Authentication SHALL use an HTTP-only session cookie set by `POST /auth/login`. Every state-changing request (`POST`, `PUT`, `PATCH`, `DELETE`) SHALL carry an `X-XSRF-TOKEN` header equal to the value of the `XSRF-TOKEN` cookie. Requests to protected endpoints without a valid session SHALL receive 401.

#### Scenario: Missing CSRF header
- **WHEN** a signed-in client sends `POST /api/submissions` without the `X-XSRF-TOKEN` header
- **THEN** the request is rejected with 403

#### Scenario: Expired session
- **WHEN** a client calls `GET /api/submissions` with an expired session
- **THEN** the response is 401

### Requirement: Submission status model
A submission SHALL have a `status` of `PENDING`, `COMPILING`, `RUNNING` or `FINISHED`. When `FINISHED` it SHALL have exactly one `verdict` from `AC` (Accepted), `WA` (Wrong Answer), `TLE` (Time Limit Exceeded), `MLE` (Memory Limit Exceeded), `RE` (Runtime Error), `CE` (Compilation Error), `OLE` (Output Limit Exceeded) or `SE` (System Error). Judging stops at the first failing test, so per-test results SHALL cover tests up to and including the first non-accepted one.

#### Scenario: Wrong answer on test 3
- **WHEN** a submission passes tests 1–2 and fails test 3 of 10
- **THEN** the submission is `FINISHED` with verdict `WA` and exactly three per-test results, the last being `WA`

### Requirement: Live submission events
`GET /submissions/{id}/events` SHALL emit a `status` event whenever the submission's status or progress changes, carrying the submission id, status, current test number and total test count while running, and verdict, maximum time and maximum memory once finished. The server SHALL send the current state immediately on connect and SHALL close the stream after the final event.

#### Scenario: Connecting to a finished submission
- **WHEN** a client opens the event stream for a submission that is already `FINISHED`
- **THEN** it receives one `status` event with the final verdict and the stream closes

#### Scenario: Progress while running
- **WHEN** the judge moves from test 4 to test 5 of 12
- **THEN** connected clients receive a `status` event with status `RUNNING`, current test 5 and total 12

### Requirement: Hidden data protection
Responses SHALL never include the input or expected output of non-sample tests. Submission source code and compiler output SHALL be returned only to the submission's owner; for other users these fields SHALL be absent.

#### Scenario: Viewing another user's submission
- **WHEN** a student requests `GET /api/submissions/{id}` for a submission they do not own
- **THEN** the response includes status, verdict, timing and per-test verdicts but no source code and no compiler output

### Requirement: Mock mode
The web app SHALL support a mock mode, enabled by configuration at development time, in which every contract endpoint—including the event stream—is answered inside the browser with demo data and no backend is contacted. Mock mode SHALL provide a demo student account, at least five problems of mixed difficulty with statements containing math and samples, and a simulated judge that walks submissions through `PENDING`, `COMPILING` and `RUNNING` to a final verdict. A developer SHALL be able to trigger each verdict on purpose. Mock mode SHALL never be active in a production build.

#### Scenario: Full flow without a backend
- **WHEN** the app runs in mock mode and the demo student signs in, opens a problem and submits code
- **THEN** the submission progresses live through each status to a final verdict

#### Scenario: Forcing a verdict
- **WHEN** a developer submits code containing the mock verdict marker for `TLE` in mock mode
- **THEN** the simulated judge finishes the submission with verdict `TLE`

#### Scenario: Production build
- **WHEN** the app is built for production
- **THEN** the build contains no mock handlers and all requests go to the real `/api`
