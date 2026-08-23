# Backend Platform Specification

## Purpose

Defines the runnable, verified modular foundation of the `api/` backend: local environment, schema baseline, module-boundary verification, domain-event infrastructure, error handling, API docs, and automated tests. Every later backend module (`iam`, `shared`, and future business modules) depends on this platform being green.

## Requirements

### Requirement: Local Environment Bootstrap

The system MUST provide a Docker Compose definition that starts a PostgreSQL instance usable by the `local` Spring profile, and MUST support distinct `local`, `test`, and `prod` Spring profiles.

#### Scenario: App boots against Compose PostgreSQL

- GIVEN the Docker Compose PostgreSQL service is running
- WHEN the application is started with the `local` profile
- THEN the application MUST start successfully and connect to that PostgreSQL instance

#### Scenario: Test profile uses isolated database

- GIVEN the application is started with the `test` profile
- WHEN tests execute against a Testcontainers-managed PostgreSQL instance
- THEN the test suite MUST NOT depend on or mutate the `local`/`prod` database

### Requirement: Database Schema Baseline

The system MUST manage schema changes exclusively through versioned Flyway migrations, starting with a baseline migration named `V1__init.sql`.

#### Scenario: Baseline migration applies cleanly

- GIVEN an empty PostgreSQL database
- WHEN the application starts
- THEN Flyway MUST apply `V1__init.sql` successfully
- AND subsequent application startups MUST NOT reapply already-applied migrations

### Requirement: Module Boundary Verification

The system MUST be organized as Spring Modulith modules, and an automated architecture test MUST verify module boundaries via `ApplicationModules.verify()`.

#### Scenario: Module verification passes

- GIVEN the current module structure of the codebase
- WHEN the module-verification test runs
- THEN `ApplicationModules.verify()` MUST complete without boundary violations

#### Scenario: Module documentation is generated

- GIVEN a successful module-verification run
- WHEN documentation generation is invoked
- THEN PlantUML module diagrams MUST be produced from the verified module structure

### Requirement: Domain Event Publication Infrastructure

The system MUST provide Spring Modulith's event-publication-registry (outbox) so domain events published by any module are durably recorded and MUST be delivered to registered listeners at least once, including after a restart with unprocessed events.

#### Scenario: Event survives listener failure

- GIVEN a domain event has been published and persisted to the publication registry
- WHEN the registered listener fails to process it on first attempt
- THEN the event MUST remain in the registry as unprocessed
- AND MUST be retried without being lost

### Requirement: Global Error Handling

The system MUST translate unhandled and validation errors into RFC 7807 `ProblemDetail` responses across all REST endpoints.

#### Scenario: Validation failure returns ProblemDetail

- GIVEN a REST endpoint with Bean Validation constraints
- WHEN a request violates a constraint
- THEN the response MUST be a `ProblemDetail` body with an appropriate 4xx status and a machine-readable error detail

#### Scenario: Unhandled exception returns ProblemDetail

- GIVEN an unexpected server-side exception occurs while processing a request
- WHEN the exception propagates to the global handler
- THEN the response MUST be a `ProblemDetail` body with status 500 and MUST NOT leak internal stack traces

### Requirement: API Documentation

The system MUST expose interactive API documentation for all implemented REST endpoints.

#### Scenario: Swagger UI is reachable

- GIVEN the application is running with the `local` profile
- WHEN a client requests the Swagger UI endpoint
- THEN the system MUST return the Swagger UI page listing the implemented endpoints

### Requirement: Automated Test Suite

The system's automated test suite MUST run against a real PostgreSQL instance via Testcontainers and MUST pass as a build gate.

#### Scenario: Full test suite passes

- GIVEN the repository at any commit on the integration branch
- WHEN `./mvnw test` is executed
- THEN all tests, including Testcontainers-backed integration tests, MUST pass
