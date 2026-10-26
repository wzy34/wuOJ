# problem-browsing Specification

## Purpose

Lets students find problems to work on and see at a glance which ones they have already solved or attempted.

## Requirements

### Requirement: Problem list
`/problems` SHALL show a paged table of problems with, for each: its display number, title, difficulty (Easy, Medium or Hard) and the signed-in student's status (Solved, Attempted or not tried). Selecting a problem title SHALL open its workspace at `/problems/:slug`.

#### Scenario: Viewing the list
- **WHEN** a student opens `/problems`
- **THEN** the first 20 problems are shown with number, title, difficulty and status

#### Scenario: Opening a problem
- **WHEN** a student clicks the title "A + B"
- **THEN** the browser navigates to `/problems/a-plus-b`

### Requirement: Solved status
A problem SHALL be marked Solved if the student has at least one accepted submission for it, Attempted if they have submissions but none accepted, and unmarked otherwise. Solved and Attempted SHALL be distinguishable by more than colour alone.

#### Scenario: Attempted then solved
- **WHEN** a student's first submission to a problem gets WA and the second gets AC
- **THEN** after the second verdict the problem shows as Solved in the list

### Requirement: Search and filter
The list SHALL support searching by title (case-insensitive, substring) and filtering by difficulty. Search input SHALL be applied after the student stops typing, without pressing Enter. Changing the search or filter SHALL return to page 1.

#### Scenario: Search by title
- **WHEN** a student types "graph" in the search box
- **THEN** only problems whose titles contain "graph" (any case) are listed, starting at page 1

#### Scenario: No matches
- **WHEN** the search matches no problems
- **THEN** an empty state reading "No problems match your search" is shown with a way to clear the search

### Requirement: Shareable list state
The current page, search text and difficulty filter SHALL be reflected in the URL, so that reloading, sharing the link or using the browser's back button restores the same view.

#### Scenario: Back to the list
- **WHEN** a student on page 3 of the list opens a problem and presses the browser back button
- **THEN** page 3 of the list is shown again with the same search and filter
