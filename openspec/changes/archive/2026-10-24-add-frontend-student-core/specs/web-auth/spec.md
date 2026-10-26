## Purpose

Lets students sign in with accounts provided by their teacher or club admin, keeps them signed in across reloads, and keeps signed-out visitors away from student pages.

## ADDED Requirements

### Requirement: Sign in
The login page SHALL ask for a username and password, both required. On success the student SHALL be taken to the page they originally requested, or to `/problems` if there was none. On failure the page SHALL show "Invalid username or password", keep the username, and clear the password. The page SHALL NOT offer self-registration.

#### Scenario: Successful sign in
- **WHEN** a student enters valid credentials on `/login`
- **THEN** they are taken to `/problems` and the top bar shows their username

#### Scenario: Wrong password
- **WHEN** a student enters a wrong password
- **THEN** the message "Invalid username or password" is shown, the username is kept and the password field is empty

#### Scenario: Empty fields
- **WHEN** a student submits the form with an empty username
- **THEN** the form shows a required-field message and no request is sent

### Requirement: Protected pages
Every route except `/login` SHALL require a signed-in student. A signed-out visitor SHALL be redirected to `/login` with the requested path remembered, and returned there after signing in. The remembered path SHALL only be followed if it is a path within the app.

#### Scenario: Deep link while signed out
- **WHEN** a signed-out visitor opens `/problems/a-plus-b`
- **THEN** they are sent to `/login`, and after signing in they land on `/problems/a-plus-b`

#### Scenario: External redirect rejected
- **WHEN** a visitor opens `/login?redirect=https://evil.example`
- **THEN** after signing in they land on `/problems`

### Requirement: Session restore
On startup the app SHALL check for an existing session before rendering any protected page, showing a full-page loading indicator meanwhile. A signed-in student visiting `/login` SHALL be redirected to `/problems`.

#### Scenario: Reload while signed in
- **WHEN** a signed-in student reloads `/submissions`
- **THEN** the submissions page is shown without the login page appearing in between

#### Scenario: Visiting login while signed in
- **WHEN** a signed-in student opens `/login`
- **THEN** they are redirected to `/problems`

### Requirement: Sign out
Choosing "Sign out" SHALL end the session on the server, discard all cached data belonging to the student in the page, and show the login page.

#### Scenario: Sign out on a shared computer
- **WHEN** student A signs out and student B signs in on the same browser
- **THEN** no problem status, submission or user data from student A is shown to student B

### Requirement: Session expiry
If any request reports that the session is no longer valid, the app SHALL show "Your session has expired. Please sign in again." and redirect to `/login`, remembering the current path. Unsent editor content SHALL be preserved.

#### Scenario: Expiry while coding
- **WHEN** a student's session expires while they are editing code and they click Submit
- **THEN** they are sent to the login page, and after signing in they return to the problem with their code intact
