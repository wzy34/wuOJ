# submission-history Specification

## Purpose

Lets students review submissions—their own and others'—with consistent verdict display, and inspect a single submission's code, per-test results and compiler output.

## Requirements

### Requirement: Verdict display
Statuses and verdicts SHALL be displayed the same way everywhere in the app: Pending, Compiling, Running, Accepted, Wrong Answer, Time Limit Exceeded, Memory Limit Exceeded, Runtime Error, Compilation Error, Output Limit Exceeded and System Error. Each SHALL have a distinct label and colour; Accepted SHALL be visually positive and failing verdicts visually negative, and in-progress statuses SHALL show an activity indicator. The meaning SHALL not rely on colour alone.

#### Scenario: Short and full labels
- **WHEN** a table cell shows a Wrong Answer verdict
- **THEN** it reads "Wrong Answer" (or "WA" with "Wrong Answer" on hover) in the failing style

### Requirement: Submissions list
`/submissions` SHALL show a paged table with submission id, submission time, user, problem, language, status or verdict, maximum time and maximum memory, newest first. It SHALL default to the student's own submissions and offer a switch to all users' submissions, plus filters for problem and verdict. Page and filters SHALL be reflected in the URL.

#### Scenario: Default view
- **WHEN** a student opens `/submissions`
- **THEN** only their own submissions are listed, newest first

#### Scenario: Filter by verdict
- **WHEN** a student selects the verdict filter "Time Limit Exceeded"
- **THEN** only TLE submissions are listed, starting at page 1, and the URL reflects the filter

#### Scenario: Shared link
- **WHEN** a student opens a copied URL `/submissions?scope=all&verdict=AC&page=2`
- **THEN** page 2 of all users' accepted submissions is shown

### Requirement: In-progress rows update
While any submission shown on the current page of the list is not finished, the list SHALL refresh periodically (at most every 5 seconds) until all shown submissions are finished.

#### Scenario: Pending row finishes
- **WHEN** the list shows a Running submission that the judge then accepts
- **THEN** within 5 seconds the row shows Accepted without a manual reload

### Requirement: Submission detail
`/submissions/:id` SHALL show the submission's id, user, problem (linking to the workspace), language, submission time, status or verdict, maximum time and maximum memory. For the owner it SHALL show the source code read-only with C++ highlighting and a copy button. It SHALL show per-test results (test number, verdict, time, memory) for the tests that were run, or the compiler output for Compilation Error. A submission that is not finished SHALL update live in the same way as the workspace result panel.

#### Scenario: Owner views a WA submission
- **WHEN** the owner opens a submission that got WA on test 3
- **THEN** the page shows the source code and three test rows, with test 3 marked Wrong Answer

#### Scenario: Viewing someone else's submission
- **WHEN** a student opens another user's submission
- **THEN** the verdict and per-test results are shown, and the source code area says "Source code is only visible to its author"

#### Scenario: Unknown submission
- **WHEN** a student opens `/submissions/999999` which does not exist
- **THEN** a "Submission not found" screen with a link to the submissions list is shown
