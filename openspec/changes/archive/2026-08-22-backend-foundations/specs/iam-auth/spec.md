# IAM & Authentication Specification

## Purpose

Defines the observable authentication and tenant-onboarding contract for the `iam` module: `Clinic`, `User`, `Role`, and `Membership` entities, and the full auth surface (onboarding, login, refresh, logout, staff invitation) needed to reach any protected endpoint with a valid JWT.

## Requirements

### Requirement: Clinic Onboarding

The system MUST allow registering a new `Clinic` (tenant) together with its initial admin `User`, MUST establish a `Membership` linking that user to the clinic with an admin `Role`, and MUST publish a `ClinicCreatedEvent` upon success.

#### Scenario: Successful onboarding

- GIVEN valid clinic and admin-user registration data
- WHEN a client submits the onboarding request
- THEN the system MUST create the `Clinic`, the admin `User`, and their `Membership`
- AND MUST publish a `ClinicCreatedEvent`
- AND MUST respond with a 201 status and the created clinic identifier

#### Scenario: Duplicate clinic registration rejected

- GIVEN a clinic already registered with a given unique identifier (e.g., email/domain)
- WHEN a client submits onboarding with the same identifier
- THEN the system MUST reject the request with a 409 status as a `ProblemDetail`
- AND MUST NOT publish a `ClinicCreatedEvent`

#### Scenario: Invalid onboarding payload rejected

- GIVEN an onboarding request missing a required field or failing a validation constraint
- WHEN the client submits the request
- THEN the system MUST respond with a 400 `ProblemDetail` listing the validation failures
- AND MUST NOT create any `Clinic`, `User`, or `Membership`

### Requirement: Login

The system MUST authenticate a user by credentials and, on success, MUST issue a JWT access token carrying the user's `clinic_id` and roles. The target clinic (tenant) for a login attempt MUST be determined server-side from the request's subdomain (e.g., `avicena.zendent.app`), and MUST NOT be determined from any clinic identifier supplied by the client in the request body.

#### Scenario: Successful login issues scoped JWT

- GIVEN a user with an active `Membership` in a clinic
- WHEN the user logs in with correct credentials on that clinic's subdomain
- THEN the system MUST respond with 200 and an access token
- AND the token's claims MUST include the user's `clinic_id` and role(s)

#### Scenario: Invalid credentials rejected

- GIVEN a login request with an incorrect password or unknown user
- WHEN the client submits the request
- THEN the system MUST respond with a 401 `ProblemDetail`
- AND MUST NOT issue any token

#### Scenario: Login rejected on a clinic where the user has no membership

- GIVEN a user with an active `Membership` in clinic A (subdomain `a.<domain>`)
- AND NO membership in clinic B (subdomain `b.<domain>`)
- WHEN the user submits correct email+password on clinic B's subdomain
- THEN the system MUST reject the login with a 401 `ProblemDetail`
- AND MUST NOT issue any token

#### Scenario: Login on unresolvable subdomain rejected

- GIVEN a login request submitted on a subdomain that does not resolve to any registered clinic slug
- WHEN the request is processed
- THEN the system MUST reject the request with a 404 `ProblemDetail`
- AND MUST NOT issue any token

#### Scenario: Apex/onboarding host is not tenant-scoped

- GIVEN a request submitted on the apex/onboarding host (e.g., `app.<domain>`)
- WHEN the request targets the login endpoint
- THEN the system MUST NOT treat the request as scoped to any clinic
- AND login on the apex host MUST be rejected or handled outside the per-clinic login contract (e.g., redirect/error), never by silently resolving an arbitrary tenant

### Requirement: Token Refresh

The system MUST allow exchanging a valid refresh token for a new access token without re-submitting credentials.

#### Scenario: Valid refresh token issues new access token

- GIVEN a previously issued, still-valid refresh token
- WHEN the client submits it to the refresh endpoint
- THEN the system MUST respond with 200 and a new access token carrying the same `clinic_id` and current roles

#### Scenario: Expired or invalid refresh token rejected

- GIVEN a refresh token that is expired, malformed, or unknown
- WHEN the client submits it to the refresh endpoint
- THEN the system MUST respond with a 401 `ProblemDetail`
- AND MUST NOT issue a new access token

### Requirement: Logout

After logout, the system's refresh token for that session MUST NOT be usable to obtain new access tokens, even though access tokens are stateless JWTs.

#### Scenario: Refresh token invalidated after logout

- GIVEN an authenticated user with a valid refresh token
- WHEN the user logs out
- THEN a subsequent refresh request using that same refresh token MUST be rejected with a 401 `ProblemDetail`

### Requirement: Staff Invitation

The system MUST allow an authorized clinic admin to invite a new staff member to their clinic, creating (or linking) a `User` with a `Membership` scoped to that clinic and a non-admin `Role`.

#### Scenario: Admin invites staff member

- GIVEN an authenticated user with an admin role in clinic A
- WHEN the admin submits a staff invitation with a valid email and role
- THEN the system MUST create the invited `Membership` scoped to clinic A
- AND MUST respond with 201

#### Scenario: Non-admin cannot invite staff

- GIVEN an authenticated user without admin privileges in clinic A
- WHEN that user submits a staff invitation request
- THEN the system MUST reject the request with a 403 `ProblemDetail`

### Requirement: Protected Endpoint Access Control

The system MUST reject any request to a protected endpoint that lacks a valid JWT, and MUST accept requests that carry one.

#### Scenario: Protected endpoint requires valid JWT

- GIVEN a protected endpoint
- WHEN a request is made without a JWT or with an expired/invalid JWT
- THEN the system MUST respond with a 401 `ProblemDetail`

#### Scenario: Protected endpoint reachable with valid JWT

- GIVEN a protected endpoint and a valid, non-expired access token
- WHEN a request is made with that token in the `Authorization` header
- THEN the system MUST process the request and respond according to the endpoint's own contract
