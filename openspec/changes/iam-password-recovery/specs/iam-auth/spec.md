# Spec delta: iam-auth — password recovery

Capability: `iam-auth`

Extends the authentication contract with self-service password recovery. Only new requirements appear here; on archive they merge into `openspec/specs/iam-auth/spec.md` alongside the existing ones. No existing requirement is modified or removed.

## ADDED Requirements

### Requirement: Password Reset Request

The system MUST allow an unauthenticated user to request a password reset for their account. The target clinic MUST be determined server-side from the request's subdomain, exactly as for login, and MUST NOT be determined from any clinic identifier supplied by the client. The response MUST NOT reveal whether an account exists for the submitted address.

#### Scenario: Reset requested for an existing account

- GIVEN a user with an active `Membership` in a clinic
- WHEN a client submits that user's email to the reset-request endpoint on that clinic's subdomain
- THEN the system MUST record a single-use reset token whose plaintext is never persisted
- AND MUST publish a domain event carrying the delivery instruction
- AND MUST respond with a 202 status and a body that names no account

#### Scenario: Reset requested for an unknown address

- GIVEN an email address with no `User` in the resolved clinic
- WHEN a client submits it to the reset-request endpoint
- THEN the system MUST respond with the same status and body as for an existing account
- AND MUST NOT record any reset token
- AND MUST NOT publish any delivery event

#### Scenario: Reset request on a clinic where the user has no membership

- GIVEN a user with an active `Membership` in clinic A only
- WHEN that user's email is submitted to the reset-request endpoint on clinic B's subdomain
- THEN the system MUST respond exactly as for an unknown address
- AND MUST NOT record a reset token usable in either clinic

#### Scenario: Reset request on an unresolvable subdomain

- GIVEN a reset request submitted on a subdomain that resolves to no registered clinic slug
- WHEN the request is processed
- THEN the system MUST reject it with a 404 `ProblemDetail`
- AND MUST NOT record any reset token

#### Scenario: Response does not wait on delivery

- GIVEN a reset request for an existing account
- WHEN the system responds
- THEN the delivery of the reset message MUST still be pending
- AND the response MUST NOT have waited on the outcome of sending it

Stating the guarantee this way keeps it verifiable. The property being protected
is that an existing account and an unknown address cannot be told apart by how
long the answer takes; asserting wall-clock latency directly would be flaky, so
the requirement is the structural cause — the response never depends on delivery.

#### Scenario: Repeated requests are rate limited

- GIVEN a client that has exceeded the permitted number of reset requests within the configured window
- WHEN it submits another reset request
- THEN the system MUST reject it with a 429 `ProblemDetail`
- AND MUST NOT publish a delivery event

### Requirement: Password Reset Completion

The system MUST allow a user holding a valid, unexpired, unused reset token to set a new password. A reset token MUST be usable at most once, MUST be stored only as a hash, and MUST expire.

#### Scenario: Valid token sets the new password

- GIVEN an unexpired, unused reset token issued for a user
- WHEN the client submits that token with a new password satisfying the validation constraints
- THEN the system MUST replace the user's stored credential
- AND MUST mark the token used
- AND MUST respond with a 204 status

#### Scenario: New password works and the old one does not

- GIVEN a user who has completed a password reset
- WHEN that user logs in on their clinic's subdomain with the new password
- THEN the system MUST issue an access token
- AND a login attempt with the previous password MUST be rejected with a 401 `ProblemDetail`

#### Scenario: Token cannot be reused

- GIVEN a reset token that has already completed a reset
- WHEN a client submits it again
- THEN the system MUST reject the request with a 400 `ProblemDetail`
- AND MUST NOT change the stored credential

#### Scenario: Expired token rejected

- GIVEN a reset token whose expiry has passed
- WHEN a client submits it
- THEN the system MUST reject the request with a 400 `ProblemDetail`
- AND MUST NOT change the stored credential

#### Scenario: Unknown or malformed token rejected

- GIVEN a reset token that was never issued, or that is malformed
- WHEN a client submits it
- THEN the system MUST reject the request with a 400 `ProblemDetail`
- AND the rejection MUST NOT distinguish an unknown token from an expired or already-used one

#### Scenario: Invalid new password rejected

- GIVEN a valid reset token and a new password that fails a validation constraint
- WHEN the client submits both
- THEN the system MUST respond with a 400 `ProblemDetail` listing the validation failures
- AND MUST NOT change the stored credential
- AND MUST NOT mark the token used

### Requirement: Password Reset Revokes Existing Sessions

Completing a password reset MUST invalidate every refresh token the user holds, so that a session opened before the reset cannot outlive it.

#### Scenario: Refresh tokens die with the reset

- GIVEN a user with one or more valid refresh tokens
- WHEN that user completes a password reset
- THEN every one of those refresh tokens MUST be rejected with a 401 `ProblemDetail` on a subsequent refresh request
- AND MUST NOT yield a new access token

### Requirement: Reset Message Delivery Survives Failure

The reset message MUST be delivered out of band from the request, and a delivery that fails MUST be retried rather than dropped.

#### Scenario: Delivery is not part of the request

- GIVEN a reset request for an existing account
- WHEN the system responds
- THEN the response MUST NOT depend on the outcome of sending the message

#### Scenario: Delivery survives a listener failure

- GIVEN a published reset-delivery event whose listener fails
- WHEN the application restarts
- THEN the event MUST still be pending delivery and MUST be republished
