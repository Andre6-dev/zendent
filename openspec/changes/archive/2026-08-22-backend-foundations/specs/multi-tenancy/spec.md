# Clinic Isolation Specification

## Purpose

Defines the observable Clinic data-isolation contract for the shared-schema backend: every Clinic-owned business entity is attributed to a Clinic, and requests are scoped so one Clinic can never observe or affect another Clinic's data.

## Requirements

### Requirement: Clinic Attribution

Every Clinic-owned business entity MUST carry a `clinic_id` attribute identifying the Clinic that owns the record.

#### Scenario: New Clinic-owned business record is Clinic-attributed

- GIVEN an authenticated user belonging to Clinic A creates a business record
- WHEN the record is persisted
- THEN the persisted record MUST have `clinic_id` equal to Clinic A's identifier

### Requirement: Clinic-Scoped Query Filtering

The system MUST scope database reads of Clinic-owned entities to the requesting user's `clinic_id` by default, without requiring each query to manually add a Clinic condition.

#### Scenario: Query returns only the caller's Clinic data

- GIVEN records exist for both Clinic A and Clinic B
- WHEN a user authenticated for Clinic A performs a listing/read query
- THEN the result set MUST contain only records where `clinic_id` equals Clinic A's identifier

### Requirement: Independent Database Enforcement

The database MUST enforce Clinic isolation on every Clinic-owned table independently of Hibernate or any other application query code. Native SQL, maintenance jobs, and other paths that bypass the ORM MUST remain subject to the active Clinic scope.

The runtime application role MUST NOT own Clinic-owned tables and MUST NOT have privileges that bypass Row-Level Security. Every Clinic-owned table MUST have Row-Level Security enabled and forced, with a policy keyed to the transaction-local `app.clinic_id` setting.

Every migration that creates a new Clinic-owned table MUST create its Row-Level Security policy in that same migration.

#### Scenario: Native query remains Clinic-scoped

- GIVEN records exist for both Clinic A and Clinic B
- AND the active database transaction is scoped to Clinic A
- WHEN application code issues a native SQL query without a `clinic_id` predicate
- THEN the database MUST return only Clinic A's rows

#### Scenario: Missing Clinic context fails closed

- GIVEN no Clinic is active for a database transaction
- WHEN the transaction reads from a Clinic-owned table
- THEN the database MUST return zero rows without a missing-setting error
- AND a write to a Clinic-owned table MUST be rejected

#### Scenario: Transaction-local scope leaves no pooled residue

- GIVEN a pooled connection completes a transaction scoped to Clinic A
- WHEN the same connection is borrowed for a later transaction with no active Clinic
- THEN the later transaction MUST NOT observe Clinic A's setting or rows

### Requirement: Per-Request Clinic Context Activation

The system MUST derive the active Clinic scope for each request from exactly one of two sources, in order of precedence, and MUST apply that scope before any Clinic-owned data is read or written:

1. **Authenticated requests**: the Clinic scope MUST be derived from the `clinic_id` claim of the caller's validated JWT. This source is AUTHORITATIVE over the request's subdomain. If the JWT's `clinic_id` does not match the Clinic resolved from the request's subdomain, the system MUST reject the request with a 403 `ProblemDetail` rather than silently activating either Clinic.
2. **Public (unauthenticated) requests**: the Clinic scope MUST be derived from the request's subdomain.

In both cases, the system MUST NOT allow the caller to override the resolved Clinic scope via client-supplied request parameters, headers, or body fields.

#### Scenario: Clinic scope derived from JWT claim

- GIVEN a valid JWT whose claims include `clinic_id` for Clinic A
- AND the request's subdomain also resolves to Clinic A
- WHEN the request is processed
- THEN the system MUST activate Clinic A as the Clinic scope for that request's entire duration
- AND MUST NOT allow the caller to override the scope via request parameters or headers

#### Scenario: Clinic scope derived from subdomain on public request

- GIVEN an unauthenticated request submitted on Clinic A's subdomain
- WHEN the request is processed
- THEN the system MUST activate Clinic A as the Clinic scope for that request

#### Scenario: JWT-vs-subdomain mismatch is rejected

- GIVEN a valid JWT whose claims include `clinic_id` for Clinic A
- WHEN that JWT is presented on a request made against Clinic B's subdomain
- THEN the system MUST reject the request with a 403 `ProblemDetail`
- AND MUST NOT activate Clinic A's, Clinic B's, or any Clinic scope for that request

### Requirement: Cross-Clinic Isolation

A user authenticated for one Clinic MUST NOT be able to read or mutate another Clinic's data, regardless of whether the other Clinic's record identifier is known or guessed.

#### Scenario: Cross-Clinic read is blocked

- GIVEN a record owned by Clinic B with a known identifier
- WHEN a user authenticated for Clinic A requests that record by identifier
- THEN the system MUST respond as if the record does not exist (404) or with 403
- AND MUST NOT return Clinic B's record data

#### Scenario: Cross-Clinic update is blocked

- GIVEN a record owned by Clinic B with a known identifier
- WHEN a user authenticated for Clinic A attempts to update or delete that record
- THEN the system MUST reject the operation (404 or 403)
- AND Clinic B's record MUST remain unchanged

#### Scenario: Cross-Clinic listing never leaks other Clinics

- GIVEN records exist across multiple Clinics including Clinic A and Clinic B
- WHEN a user authenticated for Clinic A requests any listing endpoint over Clinic-owned data
- THEN the response MUST contain zero records with `clinic_id` other than Clinic A's identifier
