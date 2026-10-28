# problem-workspace Specification

## Purpose

The page where a visitor or user reads a problem and writes a C++ solution, and where a signed-in user submits it and watches the judge's verdict arrive live, without losing their work between visits.

## Requirements

### Requirement: Workspace layout
`/problems/:slug` SHALL show the problem statement and the code editor side by side, with a draggable divider to resize them. Below 768 px wide the statement SHALL be shown above the editor. A result panel SHALL sit below the editor.

#### Scenario: Resizing panes
- **WHEN** a student drags the divider to the left
- **THEN** the statement pane narrows and the editor pane widens, within minimum widths for both

#### Scenario: Unknown problem
- **WHEN** a student opens `/problems/no-such-problem`
- **THEN** a "Problem not found" screen with a link to the problem list is shown

### Requirement: Statement rendering
The statement SHALL show the title, time limit and memory limit, followed by the statement body rendered from Markdown, including headings, lists, tables, code blocks, inline math (`$…$`) and display math (`$$…$$`). Raw HTML inside the statement SHALL NOT be rendered as HTML. Links SHALL only use safe protocols (http, https, mailto) and SHALL open in a new tab.

#### Scenario: Math in statement
- **WHEN** a statement contains `$1 \le n \le 10^5$`
- **THEN** it is displayed as typeset math, not as raw text

#### Scenario: HTML in statement
- **WHEN** a statement contains `<script>alert(1)</script>`
- **THEN** no script runs and the text is shown literally or omitted

### Requirement: Sample cases
Each sample SHALL be shown as a pair of input and expected output blocks in a monospaced font, preserving whitespace, each with a copy button that copies the exact text to the clipboard.

#### Scenario: Copy sample input
- **WHEN** a student clicks the copy button on "Sample 1 input"
- **THEN** the clipboard contains exactly the sample input text and a short "Copied" confirmation appears

### Requirement: Code editor
The editor SHALL provide C++ syntax highlighting, line numbers, bracket matching and auto-indentation, follow the active light or dark theme, and show the selected language as C++17 (the only option in this change). A new problem SHALL start with a minimal C++ template that reads from standard input. The editor SHALL keep working when the browser has no access to public content delivery networks.

#### Scenario: First visit to a problem
- **WHEN** a student opens a problem they have never edited
- **THEN** the editor contains the C++17 starter template

#### Scenario: Offline classroom network
- **WHEN** the network blocks public CDNs but allows the platform itself
- **THEN** the editor loads and works normally

### Requirement: Local drafts
Editor content SHALL be saved in the browser automatically within one second of pausing typing. Drafts SHALL be keyed by problem and by the signed-in user, or by a shared guest key for visitors. Reopening the problem SHALL restore the draft. A "Reset to template" action SHALL replace the draft with the starter template after confirmation. A failure to save locally (e.g. storage disabled) SHALL NOT break editing.

When a visitor signs in or registers, a guest draft for a problem SHALL become that user's draft for the problem if the user has no draft of their own for it. The guest draft SHALL then be removed. An existing user draft SHALL NOT be overwritten.

#### Scenario: Draft restored
- **WHEN** a user types code, closes the tab, and later reopens the same problem in the same browser
- **THEN** the editor shows the code they typed

#### Scenario: Drafts are per user
- **WHEN** user B signs in on the same browser and opens a problem user A was editing
- **THEN** user B sees their own draft or the starter template, not user A's code

#### Scenario: Guest draft carried over
- **WHEN** a visitor writes code for `a-plus-b`, chooses "Sign in to submit" and signs in as a user with no draft for that problem
- **THEN** they return to `a-plus-b` with the code they wrote as a visitor

#### Scenario: Own draft wins
- **WHEN** the user who signs in already has a draft for that problem
- **THEN** the editor shows the user's own draft

#### Scenario: Reset
- **WHEN** a user chooses "Reset to template" and confirms
- **THEN** the editor shows the starter template and the old draft is discarded

### Requirement: Submitting
For a signed-in user, a Submit button SHALL send the current editor content as a C++17 submission. Ctrl+Enter (Cmd+Enter on macOS) in the editor SHALL do the same. Submit SHALL be disabled while the code is empty or whitespace-only and while a submission request is in flight. Code larger than 64 KB SHALL be rejected before sending with a message stating the limit. If the server reports too many submissions, the app SHALL tell the user how many seconds to wait.

For a visitor, the Submit button SHALL be replaced by "Sign in to submit". It SHALL lead to the login page, which returns to the problem after signing in. Ctrl+Enter SHALL do nothing for visitors.

#### Scenario: Successful submit
- **WHEN** a user clicks Submit with valid code
- **THEN** the result panel shows the new submission as Pending within one second

#### Scenario: Double click
- **WHEN** a user double-clicks Submit
- **THEN** only one submission is created

#### Scenario: Rate limited
- **WHEN** the server responds that the user must wait 20 seconds
- **THEN** the message "Too many submissions. Try again in 20 seconds." is shown

#### Scenario: Visitor wants to submit
- **WHEN** a visitor on `/problems/a-plus-b` clicks "Sign in to submit"
- **THEN** the login page opens, and after signing in the browser returns to `/problems/a-plus-b`

### Requirement: Live verdict
After submitting, the result panel SHALL update without reloading as the submission moves through Pending, Compiling and Running ("Running test k of n") to its final verdict, then show the verdict with maximum time and memory and a link to the full submission. For Compilation Error it SHALL show the compiler output. If the live connection cannot be established or drops, the panel SHALL fall back to checking every 2 seconds until the verdict is final. Live updates SHALL stop once the verdict is final or the student leaves the page.

#### Scenario: Accepted
- **WHEN** the judge accepts a submission
- **THEN** the panel shows "Accepted" with time and memory, and the problem's status becomes Solved

#### Scenario: Compilation error
- **WHEN** the submission fails to compile
- **THEN** the panel shows "Compilation Error" and the compiler output in a monospaced block

#### Scenario: Live connection lost
- **WHEN** the live connection drops while the submission is Running
- **THEN** the panel keeps updating via periodic checks and still shows the final verdict

### Requirement: My submissions for the problem
For a signed-in user, the workspace SHALL offer a list of their own recent submissions for the current problem (newest first) with verdict, time and submission time, each linking to its detail page. Visitors SHALL NOT be offered this list.

#### Scenario: History on the problem
- **WHEN** a user who has submitted three times opens the problem's "My submissions" tab
- **THEN** the three submissions are listed newest first with their verdicts

#### Scenario: Visitor workspace
- **WHEN** a visitor opens a problem
- **THEN** no "My submissions" tab is shown
