# web-app-shell Specification

## Purpose

Provides the frame every page lives in: startup, URL routing, navigation that depends on who is signed in, light and dark themes, responsive layout, and consistent handling of missing pages and unexpected errors.

## Requirements

### Requirement: Routes
The app SHALL serve these routes:
- for everyone: `/login`, `/register`, `/problems`, `/problems/:slug`, `/submissions`, `/submissions/:id` and `/users/:username`;
- for signed-in users: `/settings`;
- for admins: `/admin/users` and `/admin/audit`.

The root path `/` SHALL redirect to `/problems`, signed in or not. Any other path SHALL show a "page not found" screen with a link back to the problem list.

#### Scenario: Root redirect
- **WHEN** a visitor opens `/`
- **THEN** the browser lands on `/problems`

#### Scenario: Unknown path
- **WHEN** anyone opens `/does-not-exist`
- **THEN** a "page not found" screen is shown with a link to `/problems`

#### Scenario: Deep link reload
- **WHEN** a user reloads the browser on `/submissions/42`
- **THEN** the same submission page is shown again

#### Scenario: Admin page as a user
- **WHEN** a signed-in user who is not an admin opens `/admin/users`
- **THEN** a "page not found" screen is shown

### Requirement: Top navigation
Every page except login and register SHALL show a top bar with the platform name, links to Problems and Submissions with the current section highlighted, and a theme toggle.
- For visitors it SHALL show "Sign in" and, while registration is open, "Register". Both SHALL return to the current page afterwards.
- For signed-in users it SHALL show a user menu with the username, containing "Profile", "Settings" and "Sign out".
- For admins it SHALL also show an "Admin" link to `/admin/users`.

#### Scenario: Current section highlighted
- **WHEN** a user is on `/submissions/42`
- **THEN** the Submissions link in the top bar is shown as active

#### Scenario: Visitor top bar
- **WHEN** a visitor opens `/problems` while registration is open
- **THEN** the top bar shows "Sign in" and "Register" and no user menu

#### Scenario: Admin top bar
- **WHEN** an admin opens any page
- **THEN** the top bar shows an "Admin" link and the user menu

### Requirement: Light and dark themes
The app SHALL offer a light and a dark theme. Until the student chooses one, it SHALL follow the operating system's colour scheme and react to changes of it. A choice made with the theme toggle SHALL be remembered in that browser and applied before the first paint on the next visit. All components, including the code editor and rendered statements, SHALL use the active theme.

#### Scenario: Following the system
- **WHEN** a student who never used the toggle switches their OS to dark mode
- **THEN** the app switches to the dark theme without a reload

#### Scenario: Remembered choice
- **WHEN** a student selects light theme, closes the tab, and opens the app again while the OS is in dark mode
- **THEN** the app opens in light theme with no flash of the dark theme

### Requirement: Desktop-first layout
The layout SHALL be designed for screens 1280 px wide and larger and SHALL remain usable down to 768 px wide without horizontal page scrolling. Below 768 px the app SHALL still render, with multi-column areas stacked vertically.

#### Scenario: Narrow window
- **WHEN** the browser window is 900 px wide
- **THEN** all pages are usable with no horizontal page scrollbar

### Requirement: Page titles
Each page SHALL set the browser tab title to the page's subject followed by the platform name, e.g. "A + B · wuOJ" on a problem page.

#### Scenario: Problem page title
- **WHEN** a student opens the problem "A + B"
- **THEN** the tab title starts with "A + B"

### Requirement: Loading and error feedback
While data for a page is loading the app SHALL show a loading placeholder in place of the content. If a request fails because of a network or server error, the app SHALL show a non-blocking error notification and offer a retry where content is missing. If rendering fails unexpectedly, the app SHALL show an error screen with a reload action instead of a blank page.

#### Scenario: Server error on list
- **WHEN** loading the problem list returns a 500 error
- **THEN** an error notification is shown and the list area offers a "Retry" action

#### Scenario: Render crash
- **WHEN** a component throws while rendering a page
- **THEN** an error screen with a "Reload" button replaces that page, and the top bar stays usable

### Requirement: English interface
All interface text, dates and component labels SHALL be in English. Dates SHALL be shown in the browser's local time zone.

#### Scenario: Component labels
- **WHEN** a paginated table is displayed
- **THEN** its pagination controls use English labels
