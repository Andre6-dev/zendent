# backend-foundations — Implementation Tasks

Phase 2 backend foundation: platform bootstrap, `iam` auth, `shared` + Clinic isolation. Backend-only (no frontend tasks). Dependency order: **2.1 blocks everything → 2.2 ∥ 2.3 run in parallel after 2.1**.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~2,600–3,600 total across the change (12 PR slices, ~150–450 lines each) |
| Chained PRs recommended | **Yes** |
| 400-line budget risk | **High** — no single phase fits in one PR; 2.1 alone needs ≥4 slices |
| Decision needed before apply | **Yes** |
| Chain strategy recommendation | Lean **feature-branch-chain**: PR-12 (mandatory Clinic-isolation test) fans in on BOTH the 2.2 and 2.3 tracks, and this is a foundational rewrite nobody wants half-merged into `main` (iam without Clinic isolation, or Clinic isolation without iam, both leave `main` in a broken security state). A tracker branch lets 2.2/2.3 land independently-reviewed but integrate before touching `main`. `stacked-to-main` is a valid alternative if the team is fine with `main` briefly having iam merged without Clinic isolation (acceptable only because there are no other consumers yet). **Orchestrator must confirm with the user before applying.** |

---

## Task 0 — Prerequisite gate (blocks the first backend commit)

- [ ] **0.1** Resolve monorepo git reconciliation: decide **absorb** (frontend history flattened) vs **subtree/submodule** (history preserved) for merging `webapp-zendent/` into a root-level monorepo. **Back up `webapp-zendent/.git` before executing any history-flattening absorb.** No backend commit (PR-1 or later) may land until this gate is closed.
  - Not designed here (per design.md, this is out-of-band). This task is a go/no-go gate, not a code task.
  - **Blocks:** all of 2.1, 2.2, 2.3.

---

## Phase 2.1 — Platform bootstrap (blocks 2.2 and 2.3)

Spec: `backend-platform/spec.md` (Local Environment Bootstrap, Database Schema Baseline, Module Boundary Verification, Domain Event Publication Infrastructure, Global Error Handling, API Documentation, Automated Test Suite). Design: D1, D3 (infra only), D4, D5, D6, D7.

- [x] **2.1.1 — MapStruct JDK-25 processor verification (EARLY, branching task)**
  Add `mapstruct` + `mapstruct-processor` 1.6.3 to `api/pom.xml` on a throwaway mapper and run `./mvnw compile` under the project's JDK 25 toolchain.
  - **If it succeeds:** keep MapStruct as the DTO-mapping standard (D5); leave the dependency in place for 2.2.
  - **If it fails:** remove the annotation-processor wiring, document the fallback (hand-written mappers) in this file's checklist, and note it for 2.2.2.
  - Blocks: 2.2.2 (needs the verdict before writing iam mappers).
  - Spec: none directly (tooling gate). Design: D5.
  - **VERDICT: MapStruct works on JDK 25 — adopted.** Probe mapper compiled and generated `MapStructProbeMapperImpl` cleanly under Corretto 25.0.1 with `maven-compiler-plugin` `annotationProcessorPaths`. The MapStruct dependency + annotation-processor wiring is KEPT in `api/pom.xml`; the throwaway probe mapper was deleted after verification. 2.2.2 should use `@Mapper(componentModel = "spring", unmappedTargetPolicy = ERROR)`, no hand-written fallback needed.

- [x] **2.1.2** Move `ApiApplication` `com.zendent.api` → `com.zendent`; repackage `ApiApplicationTests`, `TestApiApplication`, `TestcontainersConfiguration` to `com.zendent`.
  Design: D1. Spec: prerequisite for Module Boundary Verification.

- [x] **2.1.3** Create `shared` module skeleton: `com.zendent.shared.package-info` with `@ApplicationModule(type = Type.OPEN)`; empty `tenancy/`, `events/`, `web/`, `domain/` sub-packages.
  Design: D1 package map.

- [x] **2.1.4** Create `iam` module skeleton: `com.zendent.iam.package-info` (closed default); empty `domain/`, `web/`, `mapper/`, `internal/` sub-packages.
  Design: D1 package map.

- [x] **2.1.5** `com.zendent.ModularityTests`: `ApplicationModules.of(ApiApplication.class).verify()` + `new Documenter(modules).writeDocumentation()` (PlantUML under `target/spring-modulith-docs/`).
  Spec: backend-platform/Module Boundary Verification (both scenarios). Design: D1 verification wiring.
  **DoD:** test passes with zero cross-module violations; docs artifact generated.
  **STATUS: DONE.** `./mvnw test -Dtest=ModularityTests` → 2/2 green (`verifiesModuleStructure`, `writesDocumentation`). PlantUML/AsciiDoc artifacts confirmed at `api/target/spring-modulith-docs/` (`components.puml`, `module-iam.puml`, `module-shared.puml`, `all-docs.adoc`, `module-iam.adoc`, `module-shared.adoc`).

- [x] **2.1.6** Docker Compose PostgreSQL (`compose.yaml`) + `local`/`test`/`prod` Spring profiles (`application-{profile}.yaml`): datasource, Flyway `validate` on migrate, JWT secret per profile, Clinic base-domain property, `ddl-auto=validate`, Swagger on/off per profile.
  Spec: backend-platform/Local Environment Bootstrap (both scenarios). Design: "Configuration & profiles" table.
  **DoD:** app boots on `local` against Compose Postgres; `test` profile does not touch `local`/`prod` data.
  **STATUS: DONE, with one documented deviation.** `api/compose.yaml` provisions Postgres 17 for `local` (manual `docker compose up -d`, no `spring-boot-docker-compose` dependency added — kept out of scope). `application-{local,test,prod}.yaml` add datasource (local: Compose; test: none, Testcontainers `@ServiceConnection` auto-configures it; prod: `${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}` env-only), `zendent.jwt.secret` (local/test: dev secret in config with env override; prod: `${JWT_SECRET}`, no default — fails loud if unset), `zendent.tenant.base-domain` + `dev-header-override-enabled` (local/test: `localhost`+enabled; prod: `zendent.app`+disabled), and `springdoc.swagger-ui/api-docs.enabled` (on for local/test, off for prod). Base `application.yaml` carries `spring.flyway.enabled=true` + `validate-on-migrate=true` (shared). **Deviation:** `spring.jpa.hibernate.ddl-auto` is temporarily `none` instead of the designed `validate` — `spring-modulith-starter-jpa` (already on the classpath from PR-1) registers its own `event_publication` JPA entity into the persistence unit regardless of app package, so `ddl-auto=validate` fails NOW with "missing table [event_publication]" since no Flyway migration exists yet to create it (that is task 2.1.8, out of scope here). Documented inline with a `TODO(2.1.8/2.1.9)` comment in `application.yaml`; must flip to `validate` once `V1__init.sql` lands. `ApiApplicationTests` now runs `@ActiveProfiles("test")` for isolation. Verified: `./mvnw test` green (3/3: `ModularityTests` 2, `ApiApplicationTests` 1); `local`-profile boot verified live against `docker compose up -d` Postgres — "Started ApiApplication in 3.006 seconds" — then `docker compose down -v` (no leftover containers/volumes).

- [x] **2.1.7** Add `spring-boot-starter-oauth2-resource-server`; `SecurityConfig` skeleton in base package `com.zendent` with `NimbusJwtEncoder`/`NimbusJwtDecoder` beans from one HS256 `SecretKeySpec`, and the filter-chain shell (no business auth logic yet — that is 2.2/2.3).
  Design: D3 (library/pom impact), D1 (app config placement).
  **Note:** this is infra plumbing only; login/token-issuance logic is built in 2.2, Clinic-context filters are wired into this chain in 2.3.5.
  **STATUS: DONE.** `SecurityConfig` (`com.zendent`): `SecurityFilterChain` bean permits `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` and (temporarily) everything else (`anyRequest().permitAll()` — no protected business endpoint exists yet, marked with a `TODO(PKG-2.2/2.3)`); stateless session policy; CSRF disabled (stateless JWT API). `NimbusJwtEncoder`/`NimbusJwtDecoder` both built from ONE `SecretKeySpec` bean sourced from `zendent.jwt.secret` (HS256). Verified: full test suite green (14/14) + live `local`-profile boot (`docker compose up` → app boots with the JWT beans wired → clean teardown).

- [x] **2.1.8** Flyway `V1__init.sql`: `CREATE EXTENSION IF NOT EXISTS pgcrypto`; `clinic`, `app_user`, `role`, `membership`, `refresh_token`, `staff_invitation` tables with FKs/unique constraints/indexes; seed `role` catalog rows; Modulith `event_publication` DDL.
  Spec: backend-platform/Database Schema Baseline. Design: D4, D6 (schema ownership).
  **DoD:** applies cleanly against an empty DB; re-running the app does not reapply it.
  **Blocks:** 2.2.1 (entities map to these tables), 2.3.9 (isolation test needs `membership.clinic_id`).
  **STATUS: DONE.** `api/src/main/resources/db/migration/V1__init.sql`: `pgcrypto` extension, `clinic`/`app_user`/`role`/`membership`/`refresh_token`/`staff_invitation` per D4 (FKs, `unique(clinic_id,user_id)` on membership, indexes on `clinic_id`/`user_id`/`token_hash`/`token`), seeded `role` rows (ADMIN, DENTIST, STAFF), and the Modulith `event_publication` table. The `event_publication` DDL was NOT hand-guessed: it was captured by booting the app once with `ddl-auto=create` + `show-sql` against a live Postgres 17 (Compose) to get Hibernate's EXACT generated `CREATE TABLE` for `org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication` (spring-modulith-events-jpa 2.0.7), then transcribed verbatim into the migration. **Known limitation flagged (not silently fixed):** `serialized_event` is `varchar(255)` because that is Hibernate's default JPA string-column length on the library's own entity (no `@Column(length=...)` override) — real JSON event payloads can exceed 255 chars and would fail at insert time; widening it would break the `ddl-auto=validate` match, so it is left as-is with an inline SQL comment flagging it as a follow-up. Verified: applies cleanly against an empty Testcontainers DB AND a live Compose Postgres DB; `flyway_schema_history` shows version 1 applied once, re-running the app does not reapply it.

- [x] **2.1.9** Modulith event infra config: `spring.modulith.events.jdbc.schema-initialization.enabled=false`, `spring.modulith.events.republish-outstanding-events-on-restart=true`; stub `ClinicCreatedEvent` record in `com.zendent.shared.events` (fields only, no consumer yet).
  Spec: backend-platform/Domain Event Publication Infrastructure. Design: D6.
  **Rationale for placing the event class here (not in 2.3):** `iam` (2.2) publishes it and `shared` (2.3) owns its package concurrently — stubbing it in 2.1, which blocks both, avoids a cross-dependency between the two parallel tracks.
  **STATUS: DONE.** Both properties added under `spring.modulith.events` in base `application.yaml` (shared across profiles). **Note:** `jdbc.schema-initialization.enabled=false` is a documented no-op given this project's JPA-based event repository (`spring-modulith-starter-jpa`, not `-jdbc`) — kept for design fidelity (D6) and to guard against a silent double-init if the project ever switches to the JDBC-based repository. `ClinicCreatedEvent` (record: `clinicId`, `slug`, `occurredAt`) added in `com.zendent.shared.events`, fields only, no consumer/listener.

  **PKG-2.1 CLOSED.** `ddl-auto` restored from the PR-2 bridge (`none`) to the design's target `validate` — `V1__init.sql` now creates every table Hibernate needs to validate against (only `event_publication` is currently JPA-mapped; iam entities land in 2.2.1). Verified both via Testcontainers (`./mvnw test`) and a live `local`-profile boot against Compose Postgres. **This unblocks PKG-2.2 (iam) and PKG-2.3 (Clinic isolation) for parallel fan-out.**

- [x] **2.1.10** `GlobalExceptionHandler` (`@RestControllerAdvice`, `com.zendent.shared.web`) mapping validation → 400, auth → 401 (generic message), access-denied → 403, not-found → 404, conflict → 409, fallback → 500 (opaque, no stack trace); register `AuthenticationEntryPoint`/`AccessDeniedHandler` in `SecurityConfig` for filter-chain-stage errors.
  Spec: backend-platform/Global Error Handling (both scenarios). Design: D7.
  **STATUS: DONE.** `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`; maps `MethodArgumentNotValidException`/`ConstraintViolationException` → 400 with an `errors` field-map, `BadCredentialsException`/`AuthenticationException` → 401 (generic "Invalid credentials"), `AccessDeniedException` → 403, new `NotFoundException` (`com.zendent.shared.domain`) → 404, `DataIntegrityViolationException` → 409, fallback `Exception` → 500 (opaque "An unexpected error occurred", logged server-side, no stack trace in the body). `SecurityConfig` registers an `AuthenticationEntryPoint`/`AccessDeniedHandler` using a new `ProblemDetailWriter` (`com.zendent.shared.web`) so filter-chain-stage auth failures return the same RFC 7807 shape. Verified: `GlobalExceptionHandlerTest` (7/7, MockMvc standalone) + `ProblemDetailWriterTest` (2/2, unit).

- [x] **2.1.11** `OpenApiConfig` (Springdoc bean + JWT bearer security scheme); verify Swagger UI reachable under `local` profile.
  Spec: backend-platform/API Documentation.
  **STATUS: DONE.** `OpenApiConfig` (`com.zendent`) registers an `OpenAPI` bean with a `bearerAuth` HTTP-bearer/JWT security scheme. Verified two ways: (1) `ApiDocsAndSwaggerIntegrationTest` (2/2, `@SpringBootTest` + MockMvc, `test` profile) asserts `/v3/api-docs` returns 200 and contains `"bearerAuth"`, and `/swagger-ui/index.html` returns 200; (2) live `local`-profile boot against Compose Postgres — `curl /v3/api-docs` → 200 + `bearerAuth`, `curl /swagger-ui/index.html` → 200 — then clean teardown (`docker compose down -v`).

- [x] **2.1.12** Verify `TestcontainersConfiguration` still resolves after the 2.1.2 package move; add a baseline smoke test asserting the Spring context loads and `V1__init.sql` applied.
  Spec: backend-platform/Automated Test Suite, Local Environment Bootstrap (test-profile scenario).
  **STATUS: DONE.** `TestcontainersConfiguration` (already at `com.zendent` since PR-1) confirmed still resolving — `DatabaseBaselineSmokeTest` (`com.zendent`, `@Import(TestcontainersConfiguration.class)` + `@SpringBootTest` + `@ActiveProfiles("test")`) added with 3 tests: context loads AND `flyway_schema_history` records a successful version-1 migration; `role` table contains the 3 seeded rows; `event_publication` table exists (`information_schema.tables` check). 3/3 green.

- [x] **2.1.13** Close PKG-2.1 DoD: `./mvnw test` green (including `ModularityTests` + smoke test). This is the gate that unblocks 2.2 and 2.3.
  **STATUS: DONE. PKG-2.1 CLOSED.** `./mvnw test` → **17/17 green** (`GlobalExceptionHandlerTest` 7, `ProblemDetailWriterTest` 2, `ApiDocsAndSwaggerIntegrationTest` 2, `DatabaseBaselineSmokeTest` 3 (new), `ModularityTests` 2, `ApiApplicationTests` 1) — all with `spring.jpa.hibernate.ddl-auto=validate` active (the PR-2 bridge is now resolved). Also verified live: `local`-profile boot against Compose Postgres — app started, `\dt` shows all 7 baseline tables + `event_publication`, `role` seeded with 3 rows, `flyway_schema_history` shows version 1 applied — then clean teardown (`docker compose down -v`, no leftover containers/volumes). **PKG-2.2 (iam) and PKG-2.3 (Clinic isolation) are now unblocked to fan out in parallel per the PR-slice plan.**

---

## Phase 2.2 — `iam` module (parallel with 2.3; depends on 2.1)

Spec: `iam-auth/spec.md` (Clinic Onboarding, Login, Token Refresh, Logout, Staff Invitation, Protected Endpoint Access Control). Design: D2 (entity annotation only), D3, D4, D5.

- [x] **2.2.1** JPA entities + repositories: `Clinic`, `User` (`app_user`), `Role`, `Membership` (`clinicId` annotated `@TenantId` per D2), `RefreshToken`, `StaffInvitation`.
  Spec: prerequisite for all iam-auth scenarios. Design: D4 (tables), D2 (`@TenantId` placement).
  **Soft cross-dependency:** `@TenantId` compiles independently of the resolver, but it is only *functionally* isolating once `ClinicTenantIdentifierResolver` (2.3.2) is wired — call this out at PR review time, not a compile blocker.

  **STATUS: DONE (#12, #21, #24, #25). All six entities exist; `Membership.clinicId`, `RefreshToken.clinicId` and `StaffInvitation.clinicId` carry `@TenantId`.**

- [x] **2.2.2** DTOs (records) + mappers for Clinic/User/Membership/invitation request-response shapes, per the 2.1.1 verdict (MapStruct `@Mapper(componentModel="spring", unmappedTargetPolicy=ERROR)` or hand-written fallback).
  Design: D5.

  **STATUS: DONE. MapStruct maps *into* entities. Mapping *out of* one is hand-written: MapStruct reads `getX()` and these entities expose `x()`, and the fix needs an `AccessorNamingStrategy` compiled ahead of the processor — a separate module. See `api/AGENTS.md`.**

- [x] **2.2.3** `POST /auth/register` (apex/onboarding host, public): create `Clinic` + admin `User` + admin `Membership`; publish `ClinicCreatedEvent`; 409 on duplicate identifier; 400 `ProblemDetail` on validation failure.
  Spec: iam-auth/Clinic Onboarding (all 3 scenarios). Design: D3 endpoints table, D6.

  **STATUS: DONE (#12). Guarded by "no Clinic active" rather than by host classification, so the development override cannot smuggle one in.**

- [x] **2.2.4** JWT token service: build access JWT (`sub`, `clinic_id`, `roles`, `email`, `iss`, `iat`, `exp`, `jti`) via `NimbusJwtEncoder` (from 2.1.7's bean); `BCryptPasswordEncoder` wiring for hash/verify.
  Design: D3 (token strategy table).

  **STATUS: DONE (#21). Nimbus, not JJWT. The decoder validates the issuer as well as the signature (#22).**

- [x] **2.2.5** `POST /auth/login` (Clinic subdomain, public; body `{email, password}` only): resolve user by email, verify password, find `Membership` for the request's Clinic (resolved by 2.3.3's filter), issue tokens; 401 on bad credentials or no Membership in that Clinic; 404 on unresolvable subdomain (delegated to 2.3.3); apex-host login rejected/handled outside the per-Clinic contract.
  Spec: iam-auth/Login (all 5 scenarios). Design: D3 login sequence.
  **Hard cross-dependency:** requires 2.3.1–2.3.3 (`TenantContext` + `SubdomainTenantResolutionFilter`) to be functionally present for the Clinic-scoped lookup — flag at PR review if 2.3 slices lag behind.

  **STATUS: DONE (#21). Unknown email and wrong password are byte-identical, and the encoder runs on the no-such-user path so the two cost comparable time.**

- [x] **2.2.6** Refresh-token store + rotation + reuse detection (`refresh_token` hash, `rotated_from` lineage); `POST /auth/refresh` — 401 on expired/malformed/unknown token, entire-lineage revoke on reuse.
  Spec: iam-auth/Token Refresh (both scenarios). Design: D3 refresh sequence.

  **STATUS: DONE (#24). Reuse revokes the whole lineage via one recursive query. `noRollbackFor` holds that write: the refusal throws, and `REQUIRES_NEW` deadlocks against the suite's one-connection pool.**

- [x] **2.2.7** `POST /auth/logout` (bearer required): revoke presented refresh token (+ optional lineage); 204.
  Spec: iam-auth/Logout. Design: D3 logout sequence.

  **STATUS: DONE (#24).**

- [x] **2.2.8** Staff invitation: `POST /clinics/{id}/invitations` (bearer, ADMIN only — 403 for non-admin); `POST /invitations/{token}/accept` (public, invite token) creating/linking `User` + `Membership`.
  Spec: iam-auth/Staff Invitation (both scenarios).

  **STATUS: DONE (#25). Path is `/invitations`, not `/clinics/{id}/invitations` — the Clinic comes from the session, so no identifier is offered for a caller to substitute. Token stored as a hash (migration V5).**

- [x] **2.2.9** `GET /me` protected probe endpoint; verify 401 without/with expired-invalid JWT and 200 with a valid token.
  Spec: iam-auth/Protected Endpoint Access Control (both scenarios).

  **STATUS: DONE (#22). Reads the token, not the database: the claims are what the request is authorized against.**

- [x] **2.2.10** iam integration test suite (Testcontainers): full flow onboarding → login → refresh → logout → invitation-accept, plus every negative scenario in iam-auth spec.md (duplicate clinic, invalid payload, bad credentials, wrong-clinic login, unresolvable subdomain, apex-host login, expired/invalid refresh, reuse detection, non-admin invite).
  Spec: iam-auth/spec.md — full coverage gate.
  **DoD:** all iam-auth scenarios green; this is the closing task for PKG-2.2.

---

## Phase 2.3 — `shared` + Clinic isolation (parallel with 2.2; depends on 2.1)

Spec: `multi-tenancy/spec.md` (Clinic Attribution, Clinic-Scoped Query Filtering, Per-Request Clinic Context Activation, Cross-Clinic Isolation). Design: D2, D6 (contract already stubbed in 2.1.9), D1 (shared value objects).

  **STATUS: DONE, distributed. Each ticket carries its own negative scenarios; #13 is the isolation evidence.**

- [x] **2.3.1** `TenantContext` in `shared.tenancy`: `ThreadLocal<UUID>` with `set`/`get`/`clear`.
  Design: D2 components table.

  **STATUS: DONE (#10).**

- [x] **2.3.2** `ClinicTenantIdentifierResolver` implementing Hibernate `CurrentTenantIdentifierResolver<UUID>`, reading `TenantContext.get()`; confirm Spring Boot auto-wires it into the `SessionFactory`.
  Design: D2.

  **STATUS: DONE (#10).**

- [x] **2.3.3** `SubdomainTenantResolutionFilter` (`OncePerRequestFilter`, early in the chain): parse Host header; classify apex/reserved labels (`app`, `www`, `api`, bare apex) vs a real Clinic slug; resolve `Clinic` by slug (global lookup) and `TenantContext.set(...)`; 404 on unknown non-reserved slug; skip `TenantContext` for apex/reserved hosts; dev override for `local`/`test` profiles only: `{slug}.localhost` base domain + `X-Tenant-Slug` header (never enabled in `prod`).
  Spec: multi-tenancy/Per-Request Clinic Context Activation (subdomain scenario); iam-auth/Login (unresolvable-subdomain 404, apex-host scenarios). Design: D2 components table, D3 local/dev handling.
  **Blocks:** 2.2.5 (login needs the resolved Clinic).

  **STATUS: DONE (#20). Named `SubdomainClinicResolutionFilter` — `CONTEXT.md` reserves "Clinic" over "Tenant". Override header is `X-Clinic-Slug`.**

- [x] **2.3.4** `TenantContextFilter` (after the JWT resource-server auth filter): read `clinic_id` from JWT claims (authoritative); assert it matches the subdomain-resolved Clinic from 2.3.3 (else 403); overwrite `TenantContext` with the JWT Clinic; `clear()` in `finally`.
  Spec: multi-tenancy/Per-Request Clinic Context Activation (JWT scenario, mismatch scenario). Design: D2.

  **STATUS: DONE (#22), as `AuthenticatedClinicFilter`. Restores the subdomain Clinic on the way out, so an apex request cannot leak one to the next request on the thread.**

- [x] **2.3.5** Wire filter-chain order in `SecurityConfig` (2.1.7's skeleton): `SubdomainTenantResolutionFilter` → JWT resource-server auth → `TenantContextFilter`.
  Design: D2 chain-ordering note.

  **STATUS: DONE (#22). `anyRequest().permitAll()` is gone; only onboarding, login, invitation acceptance and the docs are public.**

- [x] **2.3.6** Shared value objects in `shared.domain`: `Money`, typed identifiers (e.g. `ClinicId`/`UserId` wrappers), `PageResponse<T>` pagination envelope.
  Design: D1 package map. Proposal: PKG-2.3 scope.

  **STATUS: DONE. `Money` (BigDecimal at the currency's scale, refuses to mix currencies), `PageResponse<T>` (adopted by `GET /members`, which was left unpaginated in #23 precisely because this did not exist), and `ClinicId`/`UserId` over a `TypedId` interface. The typed identifiers are **not yet adopted**: `iam` still passes raw `UUID`, and retrofitting it is a wide refactor to decide separately.**

- [x] **2.3.7** Wildcard-subdomain CORS config item: `CorsConfigurationSource` allowing `https://*.zendent.app` + the apex/onboarding origin, credentials enabled.
  Design: D3 CORS/frontend implication note.

  **STATUS: DONE. `CorsConfigurationSource` from `zendent.cors.allowed-origin-patterns`, per profile so production cannot inherit a development origin. Origin *patterns* rather than origins: a literal `*` cannot be combined with credentials, and Clinics are created at runtime. `Authorization` is an allowed header, without which every authenticated cross-origin call would fail.**

- [x] **2.3.8 — REPLACED: database-enforced Clinic-isolation gate**
  The planned repository-only test was replaced by the native `DataSource` RLS suite delivered in issues #8 and #10. A repository test cannot prove the database layer: it would stay green under Hibernate `@TenantId` even if every PostgreSQL policy were removed. The replacement uses the restricted application role, native SQL, catalog enumeration, transaction-local Clinic publication, and forced pool reuse. Repository/HTTP coverage remains in 2.3.9 for request and ORM behavior.
  Spec: multi-tenancy/Independent Database Enforcement, Cross-Clinic Isolation, Clinic-Scoped Query Filtering. Design: "Mandatory isolation tests" section.

- [x] **2.3.9** Clinic-context filter integration tests: JWT-vs-subdomain match activates the Clinic; mismatch → 403 with no Clinic activated; public request activates the Clinic from subdomain alone.
  Spec: multi-tenancy/Per-Request Clinic Context Activation (all 3 scenarios).

---

## PR-Slice Plan (chained-pr + work-unit-commits)

Chain order follows `2.1 → (2.2 ∥ 2.3)`. Each PR is one deliverable work unit with tests included.

| PR | Tasks | Rough lines | Independent DoD | Sequential/Parallel |
|---|---|---|---|---|
| PR-0 | 0.1 (git gate) | 0 (ops, no diff) | Monorepo reconciled, `webapp-zendent/.git` backed up if flattened | Sequential — blocks PR-1 |
| PR-1 | 2.1.1–2.1.5 | ~150–250 | `ModularityTests` green + docs generated; MapStruct verdict recorded | Sequential |
| PR-2 | 2.1.6 | ~150–250 | App boots on `local` vs Compose Postgres; `test` profile isolated | Sequential (after PR-1) |
| PR-3 | 2.1.7, 2.1.10, 2.1.11 | ~250–350 | ProblemDetail scenarios pass; Swagger UI reachable | Sequential (after PR-2) |
| PR-4 | 2.1.8, 2.1.9, 2.1.12, 2.1.13 | ~200–350 | Flyway applies cleanly; `./mvnw test` green — **closes PKG-2.1, unblocks fan-out** | Sequential (after PR-3) |
| PR-5 | 2.2.1–2.2.3 | ~350–400 | Onboarding scenarios (all 3) green | **Parallel start** with PR-9 |
| PR-6 | 2.2.4–2.2.5 | ~300–400 | Login scenarios (all 5) green | Sequential after PR-5; depends on PR-9 for Clinic resolution |
| PR-7 | 2.2.6–2.2.7 | ~250–350 | Refresh + logout scenarios green | Sequential after PR-6 |
| PR-8 | 2.2.8–2.2.10 | ~300–450 | Staff invitation + protected-endpoint + full iam integration suite green — **closes PKG-2.2** | Sequential after PR-7 |
| PR-9 | 2.3.1–2.3.3 | ~250–350 | Subdomain resolution + apex/reserved classification tested | **Parallel start** with PR-5 |
| PR-10 | 2.3.4–2.3.5, 2.3.7 | ~200–300 | Filter chain order verified; CORS config present | Sequential after PR-9 |
| PR-11 | 2.3.6 | ~150–250 | Value objects unit-tested | Parallel with PR-10 |
| PR-12 | 2.3.9 | ~100–200 | Filter integration scenarios green; native RLS gate already delivered by the 2.3.8 replacement — **closes PKG-2.3, fan-in** | Sequential; requires PR-5 (Membership) AND PR-10 merged |

**Dependency diagram:**

```
PR-0 (git gate)
  └─ PR-1 → PR-2 → PR-3 → PR-4  (PKG-2.1, sequential, blocks fan-out)
                              ├─ PR-5 → PR-6 → PR-7 → PR-8         (PKG-2.2 track)
                              └─ PR-9 → PR-10 ─┐
                                     └─ PR-11 ──┤
                                                 └─ PR-12 (needs PR-5 + PR-10)   (PKG-2.2 ∥ PKG-2.3 fan-in)
```

**Parallelizable:** PR-5↔PR-9 start together; PR-10↔PR-11 can run together; the two tracks (PR-5→PR-8 and PR-9→PR-12) proceed independently except the PR-12 fan-in.
**Strictly sequential:** PR-0→PR-1→PR-2→PR-3→PR-4 (each depends on the prior); PR-6 needs PR-9 merged (Clinic resolution) before login can be tested end-to-end even though it's a separate track.

---

## Design cross-cutting checklist (traceability — mirrors design.md)

  **STATUS: DONE (#22, #13).**

- [x] `ApiApplication` moved to `com.zendent`; tests repackaged. → 2.1.2
- [x] `ModularityTests` runs `verify()` + writes PlantUML docs. → 2.1.5
- [x] `shared` marked OPEN; `iam` closed with named-interface surface. → 2.1.3, 2.1.4
- [ ] `@TenantId` on `Membership.clinicId`; `ClinicTenantIdentifierResolver` wired into the `SessionFactory`. → 2.2.1, 2.3.2
- [ ] `SubdomainTenantResolutionFilter` (early) resolves the Clinic from Host; apex/reserved labels skipped; unknown slug → 404. → 2.3.3
- [ ] `TenantContextFilter` (after JWT auth) applies the authoritative JWT Clinic and asserts subdomain == JWT `clinic_id` (mismatch → 403). → 2.3.4
- [ ] `local`/`test` dev override: `{slug}.localhost` + `X-Tenant-Slug` header (never enabled in `prod`). → 2.3.3
- [ ] Wildcard-subdomain CORS noted as a config item. → 2.3.7
- [x] Native RLS Clinic-isolation gate green; repository-only plan replaced because it cannot prove database enforcement. → 2.3.8 replacement (#8, #10)
- [ ] `spring-boot-starter-oauth2-resource-server` added; HS256 encoder/decoder from one secret. → 2.1.7
- [ ] `refresh_token` store with rotation + reuse detection; logout revokes. → 2.2.6, 2.2.7
- [x] `V1__init.sql` baseline (extensions + iam + refresh + invitation + Modulith `event_publication`). → 2.1.8
- [x] MapStruct added (or hand-written fallback if JDK 25 processor fails). → 2.1.1, 2.2.2 — verdict: adopted, works on JDK 25.
- [x] `ClinicCreatedEvent` in `shared.events`; outbox schema in Flyway, auto-init disabled. → 2.1.9
- [ ] `ProblemDetail` advice + Security entry-point/denied handlers. → 2.1.10
- [ ] Monorepo git reconciliation resolved BEFORE first commit. → 0.1

## Next step

Proceed to `sdd-apply`, starting with Task 0 (git gate), then PR-1 through PR-4 (PKG-2.1) sequentially, then fan out to PKG-2.2 and PKG-2.3 in parallel per the PR-slice plan.
