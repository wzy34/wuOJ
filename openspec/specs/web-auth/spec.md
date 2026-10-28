# web-auth Specification

## Purpose

Lets visitors browse without an account, register, and sign in; keeps users signed in across reloads; lets them change their password; and sends people to sign in only for pages and actions that need an account.

## Requirements

### Requirement: Sign in
The login page SHALL ask for a username and password, both required. On success the user SHALL be taken to the page they originally requested, or to `/problems` if there was none. On failure the page SHALL show "Invalid username or password", keep the username, and clear the password. While registration is open, the page SHALL link to the register page, passing on the page to return to.

#### Scenario: Successful sign in
- **WHEN** a user enters valid credentials on `/login`
- **THEN** they are taken to `/problems` and the top bar shows their username

#### Scenario: Wrong password
- **WHEN** a user enters a wrong password
- **THEN** the message "Invalid username or password" is shown, the username is kept and the password field is empty

#### Scenario: Empty fields
- **WHEN** a user submits the form with an empty username
- **THEN** the form shows a required-field message and no request is sent

### Requirement: Protected pages
`/settings` SHALL require a signed-in user. `/admin/*` SHALL require a signed-in admin. Every other page SHALL be open to visitors.
- A visitor who opens a page that needs sign-in SHALL be redirected to `/login` with the requested path remembered, and returned there after signing in.
- A signed-in user who is not an admin SHALL get the "page not found" screen for `/admin/*`.
- The remembered path SHALL only be followed if it is a path within the app.

#### Scenario: Deep link while signed out
- **WHEN** a visitor opens `/problems/a-plus-b`
- **THEN** the problem is shown without a redirect to `/login`

#### Scenario: Settings while signed out
- **WHEN** a visitor opens `/settings`
- **THEN** they are sent to `/login`, and after signing in they land on `/settings`

#### Scenario: External redirect rejected
- **WHEN** a visitor opens `/login?redirect=https://evil.example`
- **THEN** after signing in they land on `/problems`

### Requirement: Session restore
On startup the app SHALL check for an existing session before rendering any page, showing a full-page loading indicator meanwhile, so that a signed-in user never sees the visitor view first. A signed-in user visiting `/login` or `/register` SHALL be redirected to `/problems`. The check SHALL be repeated when the browser tab becomes active again.

#### Scenario: Reload while signed in
- **WHEN** a signed-in user reloads `/submissions`
- **THEN** their own submissions are shown without the login page or the visitor view appearing in between

#### Scenario: Visiting login while signed in
- **WHEN** a signed-in user opens `/login`
- **THEN** they are redirected to `/problems`

### Requirement: Sign out
Choosing "Sign out" SHALL end the session on the server, discard all cached data belonging to the user in the page, and show `/problems` as a visitor.

#### Scenario: Sign out on a shared computer
- **WHEN** user A signs out and user B signs in on the same browser
- **THEN** no problem status, submission or user data from user A is shown to user B

#### Scenario: Visitor after sign out
- **WHEN** a user signs out
- **THEN** the problem list is shown with "Sign in" in the top bar and no status column

### Requirement: Session expiry
If a request reports that the session is no longer valid, or a repeated session check finds the user signed out, the app SHALL:
- show "Your session has expired. Please sign in again.";
- discard the user's cached data;
- continue as a visitor;
- redirect to `/login`, remembering the current path, only when the current page or action needs sign-in.

Unsent editor content SHALL be preserved.

#### Scenario: Expiry while coding
- **WHEN** a user's session expires while they are editing code and they click Submit
- **THEN** they are sent to the login page, and after signing in they return to the problem with their code intact

#### Scenario: Expiry while browsing
- **WHEN** a user's session is revoked while they are on `/problems` and they return to the tab
- **THEN** the message is shown and the list is shown as a visitor, without a redirect

### Requirement: Registration page
While registration is open, `/register` SHALL ask for a username, a password and a password confirmation, all required.
- Before sending, it SHALL check that the username uses only letters, digits, `.`, `_` and `-` (at most 64 characters), that the password is 8–128 characters, and that the confirmation matches. It SHALL show a message next to the offending field.
- Field errors from the server, such as a taken username or a common password, SHALL be shown next to the field they name.
- On success the new user SHALL be signed in and taken to the page they came from, or to `/problems`.
- The page SHALL link back to the login page.

While registration is closed, `/register` SHALL say "Registration is closed." and link to the login page.

#### Scenario: Register and continue
- **WHEN** a visitor on `/problems/a-plus-b` chooses "Register" and registers as `carol`
- **THEN** they land on `/problems/a-plus-b` signed in as `carol`

#### Scenario: Taken username
- **WHEN** a visitor registers with a username that is already taken
- **THEN** the server's message is shown next to the username field, and both passwords are kept

#### Scenario: Mismatched confirmation
- **WHEN** the password and its confirmation differ
- **THEN** a message is shown next to the confirmation and no request is sent

### Requirement: Settings and password change
`/settings` SHALL let a signed-in user change their password by entering the current password, a new password and its confirmation. It SHALL:
- check the same password lengths as the register page before sending;
- show server field errors next to their fields;
- on success, confirm with "Password changed. You were signed out on your other devices." and clear the form.

The page SHALL state that there is no password recovery and that an admin can reset a forgotten password.

#### Scenario: Change password
- **WHEN** a user enters the right current password and a valid new password
- **THEN** the confirmation is shown, the form is cleared, and the user stays signed in

#### Scenario: Wrong current password
- **WHEN** the current password is wrong
- **THEN** the server's message is shown next to the current-password field

### Requirement: Forced password change in the app
While the signed-in user must change their password, every page SHALL redirect to `/settings`. The page SHALL then explain that an admin reset the password and a new one must be chosen. After a successful change the user SHALL be taken to the page they first requested, or to `/problems`. "Sign out" SHALL stay available.

#### Scenario: Signing in with a temporary password
- **WHEN** a user signs in with a temporary password set by an admin and was heading to `/submissions`
- **THEN** `/settings` is shown with the explanation, and after changing the password they land on `/submissions`
