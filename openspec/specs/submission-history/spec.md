# submission-history Specification

## Purpose

Lets visitors and users review submissions—their own and others'—with consistent verdict display, and inspect a single submission's per-test results, plus its code and compiler output for the owner and admins.

## Requirements

### Requirement: Verdict display
Statuses and verdicts SHALL be displayed the same way everywhere in the app: Pending, Compiling, Running, Accepted, Wrong Answer, Time Limit Exceeded, Memory Limit Exceeded, Runtime Error, Compilation Error, Output Limit Exceeded and System Error. Each SHALL have a distinct label and colour; Accepted SHALL be visually positive and failing verdicts visually negative, and in-progress statuses SHALL show an activity indicator. The meaning SHALL not rely on colour alone.

#### Scenario: Short and full labels
- **WHEN** a table cell shows a Wrong Answer verdict
- **THEN** it reads "Wrong Answer" (or "WA" with "Wrong Answer" on hover) in the failing style

### Requirement: Submissions list
`/submissions` SHALL show a paged table with submission id, submission time, user, problem, language, status or verdict, maximum time and maximum memory, newest first.
- For a signed-in user it SHALL default to their own submissions and offer a switch to all users' submissions.
- For a visitor it SHALL always show all users' submissions and SHALL NOT offer the switch.
- It SHALL offer filters for problem and verdict.
- It SHALL accept a user filter from the URL, shown as a removable filter tag.
- Page and filters SHALL be reflected in the URL.

Each username in the table SHALL link to that user's profile.

#### Scenario: Default view
- **WHEN** a user opens `/submissions`
- **THEN** only their own submissions are listed, newest first

#### Scenario: Visitor view
- **WHEN** a visitor opens `/submissions`
- **THEN** all users' submissions are listed, newest first, with no "mine/all" switch and without being sent to the login page

#### Scenario: Filter by verdict
- **WHEN** a user selects the verdict filter "Time Limit Exceeded"
- **THEN** only TLE submissions are listed, starting at page 1, and the URL reflects the filter

#### Scenario: Shared link
- **WHEN** a user opens a copied URL `/submissions?scope=all&verdict=AC&page=2`
- **THEN** page 2 of all users' accepted submissions is shown

#### Scenario: One user's submissions
- **WHEN** anyone opens `/submissions?scope=all&user=alice`
- **THEN** only alice's submissions are listed, and a filter tag "User: alice" can be removed to show everyone's

### Requirement: In-progress rows update
While any submission shown on the current page of the list is not finished, the list SHALL refresh periodically (at most every 5 seconds) until all shown submissions are finished.

#### Scenario: Pending row finishes
- **WHEN** the list shows a Running submission that the judge then accepts
- **THEN** within 5 seconds the row shows Accepted without a manual reload

### Requirement: Submission detail
`/submissions/:id` SHALL show the submission's id, user (linking to their profile), problem (linking to the workspace), language, submission time, status or verdict, maximum time and maximum memory.
- For the owner and for admins it SHALL show the source code read-only with C++ highlighting and a copy button, and the compiler output for Compilation Error.
- For everyone else the source code area SHALL say "Source code is only visible to its author".
- It SHALL show per-test results (test number, verdict, time, memory) for the tests that were run.
- For a signed-in user, a submission that is not finished SHALL update live in the same way as the workspace result panel. For a visitor it SHALL be re-checked every 2 seconds until finished.

#### Scenario: Owner views a WA submission
- **WHEN** the owner opens a submission that got WA on test 3
- **THEN** the page shows the source code and three test rows, with test 3 marked Wrong Answer

#### Scenario: Viewing someone else's submission
- **WHEN** a user or visitor opens another user's submission
- **THEN** the verdict and per-test results are shown, and the source code area says "Source code is only visible to its author"

#### Scenario: Admin reviews code
- **WHEN** an admin opens another user's submission
- **THEN** the source code is shown with the copy button

#### Scenario: Unknown submission
- **WHEN** anyone opens `/submissions/999999` which does not exist
- **THEN** a "Submission not found" screen with a link to the submissions list is shown
