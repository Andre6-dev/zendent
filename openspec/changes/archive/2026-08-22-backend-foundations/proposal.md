# backend-foundations — Startable, verified modular backend with multi-tenant iam

This change delivers Phase 2 of zendent-app: a runnable Spring Boot 4 backend with Spring Modulith verified module boundaries, enforced multi-tenant isolation, and a working `iam` module (authentication + clinic onboarding). It converts the near-empty `api/` scaffold into a foundation that unblocks every core business module in later phases.

**Affected side:** backend (primary). One repo-structure item touches the whole product: reconciling the frontend's existing `.git` into a root monorepo.

## Intent

### Problem
`api/` is a near-empty Spring Boot 4.0.7 scaffold (only `ApiApplication.java`, `application.yaml`, empty `db/migration/`, test scaffolding). There is no module structure, no persistence, no auth, no tenancy, and no way to run the app against a database. No later backend phase (staff, patients, treatments, reservations, billing, inventory, purchases, reporting, support) can start until a verified modular foundation with tenant isolation and working auth exists.

### Why now
This is the dependency root of all backend work. Every subsequent module depends on: a bootable platform, verified Modulith boundaries, the tenancy filter, and `iam` auth issuing JWTs that carry `clinic_id` and roles. Delaying it blocks the entire backend roadmap.

### Success
- `./mvnw test` is green with Testcontainers (real PostgreSQL).
- App boots against the Docker Compose PostgreSQL and Swagger UI is reachable.
- `ApplicationModules.of(...).verify()` passes and module docs (PlantUML) are generated.
- Full auth flow works end to end: clinic onboarding → login → refresh → logout → staff invitation, with a protected endpoint reachable only with a valid JWT.
- Tenant isolation is proven by mandatory tests: a user of clinic A cannot see clinic B's data.

## Scope

### In scope (complete foundation — all three PKGs)

**PKG-2.1 — Platform bootstrap** _(blocks the rest)_
- Docker Compose with PostgreSQL; Spring profiles `local` / `test` / `prod`.
- Flyway baseline migration `V1__init.sql` (extensions + minimal common tables).
- Spring Modulith configuration + `ApplicationModules.verify()` architecture test + PlantUML docs generation.
- Global error handling with `ProblemDetail` (RFC 7807).
- Swagger UI (Springdoc OpenAPI) reachable.

**PKG-2.2 — Module `iam` (auth + tenant)** _(depends on 2.1)_
- Entities: `Clinic` (tenant), `User`, `Role`, `Membership` (user ↔ clinic ↔ role).
- **Full auth surface**: clinic registration (onboarding), login, refresh, logout, staff invitation.
- JWT carries `clinic_id` and roles.
- Clinic onboarding publishes `ClinicCreatedEvent`.
- DTOs + Bean Validation; endpoints documented via Springdoc.

**PKG-2.3 — `shared` + tenancy** _(parallel with 2.2 after 2.1)_
- Common value objects: `Money`, typed identifiers, pagination.
- Tenancy infrastructure: Hibernate `@FilterDef` / `@Filter` scoping by `clinic_id` + a Spring Security interceptor that activates the filter from the authenticated JWT's `clinic_id`.
- Spring Modulith event infrastructure (event-publication-registry / outbox) and shared domain event contracts.
- **Mandatory tenant-isolation tests**: clinic A cannot read clinic B data.

### Out of scope
- Any non-foundation business module: `staff`, `patients`, `treatments`, `reservations`, `billing`, `inventory`, `purchases`, `reporting`, `support`.
- Real business endpoints beyond `iam` (only auth + onboarding + invitation are built).
- The frontend mock → real-API swap (that is a later phase).
- Production deployment, CI pipelines, secrets management hardening (beyond profile separation).

## High-level approach

Details belong to the design phase; this is the shape, not the blueprint.

| Area | Direction |
|------|-----------|
| Build tool | **Maven** (`./mvnw`) — authoritative over stale docs that say Gradle. |
| Base package | **`com.zendent`** — modules are top-level packages `com.zendent.iam`, `com.zendent.shared` (docs' `com.zendenta` is stale). |
| Module layout | Spring Modulith: each module a top-level package; internals hidden; cross-module coupling only via published events + an optional public `api`/`spi` package. First modules: `iam` and `shared`. |
| Persistence | Spring Data JPA + PostgreSQL; schema via Flyway versioned migrations starting at `V1__init.sql`. |
| Multi-tenancy | Shared schema; every business entity carries `clinic_id`. Hibernate filter activated per-request from the JWT `clinic_id` via a Security interceptor. |
| Auth | Spring Security + JWT; token carries `clinic_id` + roles; full onboarding/login/refresh/logout/invitation surface. |
| Events | `ApplicationEventPublisher` + `@ApplicationModuleListener`; Modulith event-publication-registry (outbox) for reliable delivery; contracts declared in `shared`. |
| API surface | REST controllers + DTOs (no JPA entities exposed), Bean Validation, Springdoc/Swagger, `ProblemDetail` errors. |
| Mapping | If MapStruct is adopted for DTO↔entity mapping, **adding the `mapstruct` dependency to `pom.xml` is an explicit scope item** — the current pom does NOT include it. The design phase decides MapStruct vs. hand-written mappers. |

### Intended delivery order (not planned here)
This is a LARGE change — complete foundation + full auth + monorepo migration — and **will exceed a 400-line single-PR budget**. The proposal covers the complete foundation, but delivery is expected to be sliced into chained PRs following the plan's dependency order:

```
2.1 bootstrap ──► 2.2 iam
              └─► 2.3 tenancy      (2.2 ∥ 2.3 after 2.1)
```

The task breakdown and PR slicing are decided in the tasks phase, not here.

## Prerequisite risk — monorepo git reconciliation (resolve BEFORE first backend commit)

The user confirmed git will be a **monorepo at the product root `zendent-app/`**. But `webapp-zendent/` already has its **own `.git` with history**, and the product root is not currently a git repo.

Before the first backend commit, decide and execute one of:
- **Absorb** the frontend into a root repo (frontend history is lost/flattened), or
- **Subtree / submodule** the frontend to preserve its history under the root repo.

This is a blocking prerequisite, called out explicitly rather than silently. It touches product repo structure, so its impact is cross-cutting even though the code change is backend-only.

## Rollback plan

Per the config `proposal` rule (rollback required for risky changes):

- **Code**: all new code is additive under new packages (`com.zendent.iam`, `com.zendent.shared`, platform config). Rollback = revert the new packages/commits; the scaffold returns to its current near-empty state.
- **Schema**: this change introduces the Flyway baseline and `iam`/tenancy migrations. Rollback = drop this change's Flyway migrations and, in a non-production/local environment, drop and recreate the database (or restore from snapshot). Because `V1__init.sql` is the baseline, there is no prior committed schema to fall back to — reverting removes the schema entirely rather than downgrading it.
- **DB state implication**: any data created during onboarding testing is disposable (local/test only via Testcontainers and Docker Compose). No production data exists yet, so rollback carries no production-data risk at this phase.
- **Monorepo**: if the git reconciliation is done via subtree/submodule it is reversible; if done via history-flattening absorb, keep a backup clone of `webapp-zendent/.git` before executing so the frontend history is recoverable.

## Next step
Proceed to `sdd-spec` and `sdd-design` (they can run in parallel): spec captures Given/When/Then for onboarding/login/refresh/logout/invitation and tenant isolation; design details the Modulith boundaries, tenancy filter wiring, and event infrastructure.
