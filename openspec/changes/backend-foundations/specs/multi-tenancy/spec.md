# Multi-Tenancy Specification

## Purpose

Defines the observable multi-tenant data-isolation contract for the shared-schema backend: every business entity is attributed to a clinic (tenant), and requests are scoped so one clinic can never observe or affect another clinic's data.

## Requirements

### Requirement: Tenant Attribution

Every business entity MUST carry a `clinic_id` attribute identifying the tenant that owns the record.

#### Scenario: New business record is tenant-attributed

- GIVEN an authenticated user belonging to clinic A creates a business record
- WHEN the record is persisted
- THEN the persisted record MUST have `clinic_id` equal to clinic A's identifier

### Requirement: Tenant-Scoped Query Filtering

The system MUST scope database reads of tenant-owned entities to the requesting user's `clinic_id` by default, without requiring each query to manually add a tenant condition.

#### Scenario: Query returns only the caller's tenant data

- GIVEN records exist for both clinic A and clinic B
- WHEN a user authenticated for clinic A performs a listing/read query
- THEN the result set MUST contain only records where `clinic_id` equals clinic A's identifier

### Requirement: Per-Request Tenant Context Activation

The system MUST derive the active tenant scope for each request from exactly one of two sources, in order of precedence, and MUST apply that scope before any tenant-owned data is read or written:

1. **Authenticated requests**: the tenant scope MUST be derived from the `clinic_id` claim of the caller's validated JWT. This source is AUTHORITATIVE over the request's subdomain. If the JWT's `clinic_id` does not match the clinic resolved from the request's subdomain, the system MUST reject the request with a 403 `ProblemDetail` rather than silently activating either clinic.
2. **Public (unauthenticated) requests**: the tenant scope MUST be derived from the request's subdomain.

In both cases, the system MUST NOT allow the caller to override the resolved tenant scope via client-supplied request parameters, headers, or body fields.

#### Scenario: Tenant scope derived from JWT claim

- GIVEN a valid JWT whose claims include `clinic_id` for clinic A
- AND the request's subdomain also resolves to clinic A
- WHEN the request is processed
- THEN the system MUST activate clinic A as the tenant scope for that request's entire duration
- AND MUST NOT allow the caller to override the scope via request parameters or headers

#### Scenario: Tenant scope derived from subdomain on public request

- GIVEN an unauthenticated request submitted on clinic A's subdomain
- WHEN the request is processed
- THEN the system MUST activate clinic A as the tenant scope for that request

#### Scenario: JWT-vs-subdomain mismatch is rejected

- GIVEN a valid JWT whose claims include `clinic_id` for clinic A
- WHEN that JWT is presented on a request made against clinic B's subdomain
- THEN the system MUST reject the request with a 403 `ProblemDetail`
- AND MUST NOT activate clinic A's, clinic B's, or any tenant scope for that request

### Requirement: Cross-Tenant Isolation

A user authenticated for one clinic MUST NOT be able to read or mutate another clinic's data, regardless of whether the other clinic's record identifier is known or guessed.

#### Scenario: Cross-tenant read is blocked

- GIVEN a record owned by clinic B with a known identifier
- WHEN a user authenticated for clinic A requests that record by identifier
- THEN the system MUST respond as if the record does not exist (404) or with 403
- AND MUST NOT return clinic B's record data

#### Scenario: Cross-tenant update is blocked

- GIVEN a record owned by clinic B with a known identifier
- WHEN a user authenticated for clinic A attempts to update or delete that record
- THEN the system MUST reject the operation (404 or 403)
- AND clinic B's record MUST remain unchanged

#### Scenario: Cross-tenant listing never leaks other tenants

- GIVEN records exist across multiple clinics including clinic A and clinic B
- WHEN a user authenticated for clinic A requests any listing endpoint over tenant-owned data
- THEN the response MUST contain zero records with `clinic_id` other than clinic A's identifier
