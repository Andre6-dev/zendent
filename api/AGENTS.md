# api — backend conventions

Spring Modulith backend for Zendenta. Read this before writing Java in `api/`.

- Domain vocabulary (Clinic, Membership, Dentist, Staff…): `../CONTEXT.md`. Use those words in types, columns, and messages; never their `_Avoid_` synonyms.
- Why a decision is the way it is: `../docs/adr/`. ADR 0008 governs everything under "Clinic isolation" below.
- What is being built and in what order: `../openspec/changes/backend-foundations/tasks.md`.

## Clinic isolation

Isolation is enforced in two layers, and application code must never be the only thing standing between two Clinics.

**Layer 1 — Hibernate.** Every tenant-owned entity carries `@TenantId` on its `clinicId` field. See `Membership.java:22`.

**Layer 2 — PostgreSQL RLS.** Policies filter on the `app.clinic_id` session setting. `ClinicTransactionListener` publishes it with `SET LOCAL` at `afterBegin`, so it dies with the transaction and cannot ride a pooled connection into the next request.

A tenant-owned table is unreachable with no Clinic set. That is the fail-closed behaviour working — resolve it by setting the Clinic, never by loosening a policy or reaching for a role that bypasses RLS.

**The active Clinic comes from the subdomain or a validated JWT claim.** A request never influences its own tenant scope through a parameter, a header, or a body field.

`OnboardingClinicScope` is the single bounded exception: Clinic registration sets the tenant to the Clinic it just created, inside the same transaction. It is deliberately confined to `iam.internal` and guards its own preconditions. Treat it as a sealed exception rather than a pattern to copy — a second site deriving its tenant from request-produced data is the vulnerability ADR 0008 exists to prevent.

## HTTP surface

**Every endpoint is documented.** A controller method carries `@Tag` on the class, `@Operation` on the method, and an `@ApiResponse` for each status it can actually produce — including the ones `GlobalExceptionHandler` emits on its behalf (400, 401, 403, 404, 409). Springdoc infers the happy path only; every failure an integration test asserts is a failure the OpenAPI document must name.

**Every endpoint requires a session unless it is listed as public.** Onboarding and login are public because one runs before a Clinic exists and the other is how a session is obtained; the API docs are public per profile. Adding a route makes it authenticated by default — widening that is a deliberate edit to the public list, never an oversight.

**Errors are RFC 7807 `ProblemDetail`, from one of two places:**

- `GlobalExceptionHandler` — anything thrown after MVC dispatch.
- `ProblemDetailWriter`, wired into `SecurityConfig` — anything thrown inside the Spring Security filter chain, before a controller runs.

Both paths return the same shape. A controller that builds an error response by hand breaks that guarantee; throw the exception and let the handler map it.

**User-facing messages live in `com.zendent.shared.domain.ErrorMessages`, not in the throw site.** Two callers rejecting the same condition must not describe it two ways, and a message the API returns is part of the contract. Add the constant there and reference it; a literal at a throw site is the thing this rule exists to prevent.

Messages returned on an authentication failure stay generic. `"Invalid credentials"` never narrows to which half was wrong.

**A listing returns `PageResponse<T>`, never Spring's `Page`.** Spring's shape is its internals and is not stable across versions; the envelope in `shared.domain` is the API's own contract.

## Module boundaries

Each module is a top-level package under `com.zendent`, described in its `package-info.java`.

- `shared` is `OPEN` — infrastructure and published contracts, freely depended on, and holds no business logic.
- Every other module is closed. `domain`, `internal`, `web`, and `mapper` are module-private; expose what other modules need as a `@NamedInterface` or, preferably, a domain event owned by `shared.events`.

`ModularityTests` fails the build on a violation. When it does, move the collaboration to an event rather than widening `allowedDependencies`.

## Persistence

- Schema changes are Flyway migrations under `db/migration/`. `ddl-auto` is `validate`; Hibernate never writes DDL.
- Entities expose record-style accessors — `clinic.id()`, `clinic.slug()` — with no `get` prefix. A JPA no-arg constructor is `protected`; the real constructor takes what the invariant requires.
- DTOs are records. Mapping *into* an entity is MapStruct's job — `@Mapper(componentModel = "spring", unmappedTargetPolicy = ERROR)` — because it writes through constructors.
- Mapping *out of* an entity is written by hand. MapStruct's default accessor strategy reads `getX()` and these entities expose `x()`; teaching it otherwise needs an `AccessorNamingStrategy` on the annotation-processor path, which must be compiled before the processor runs and so cannot live in this module. Reach for a plain `@Component` in `mapper/`, as `MemberMapper` does.

## Tests

Persistence is never mocked. Integration tests run against real PostgreSQL via Testcontainers and the real filter chain, and live in `com.zendent` as `*IntegrationTest`; unit tests sit beside the package they cover.

Integration tests pin the connection pool to one connection, so that a tenant leaking across a reused connection shows up as a failure rather than as luck. That budget is a constraint on production code too: `REQUIRES_NEW` needs a second connection and deadlocks under it. When work must survive a rollback, reach for `noRollbackFor` instead.

Anything asserting isolation proves it at the seam that can distinguish the two layers. A test that only exercises `@TenantId` passes with RLS disabled and proves half of ADR 0008 — reach for native SQL on a raw connection to prove the policy itself, as `RowLevelSecurityIntegrationTest` does.

## Style

Tabs, Spring's Java conventions. No formatter plugin enforces this, so match the file you are editing.

Beans take their collaborators through a constructor; injected fields are `final`. Constructors of `@Service`/`@Component`/`@RestController` classes are package-private, as are controller handler methods — nothing widens visibility past what its own module needs.
