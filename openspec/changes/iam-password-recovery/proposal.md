# iam-password-recovery — Self-service password reset with real email delivery

Adds `POST /auth/forgot-password` and `POST /auth/reset-password` to the `iam` module, plus the mail infrastructure the product does not have yet. It closes the only gap that stands between a locked-out user and their Clinic.

**Affected side:** backend only. No frontend work — the `/forgot-password` and `/reset-password` screens belong to the `frontend-auth-shell` change that follows this one.

## Intent

### Problem

`screens.md` lists password recovery as part of Auth, but `AuthController` has only `register`, `login`, `refresh` and `logout`. A user who forgets their password today has no way back into their Clinic, and no administrator can put them back either: `iam` exposes invitation and revocation, never a credential reset.

The obvious shortcut does not work. `InvitationResponse` documents the product's existing pattern — *"Returned once, to the administrator who issued it. The `token` is the only plaintext copy that ever exists — so it is theirs to deliver."* An administrator can hand-deliver an invitation because the invited person is reachable by other means and the administrator is authenticated. Neither holds for a reset: the person asking is unauthenticated and locked out, and returning the token to an unverified requester **is** the vulnerability. And per ADR 0005, a single-dentist practice is also a Clinic — there the administrator *is* the locked-out person, so an admin-mediated reset fails exactly the smallest customer.

Password recovery therefore requires the product's first outbound email.

### Why now

`frontend-auth-shell` builds the login surface the whole of Phase 3 depends on. Shipping a login screen whose "Forgot password?" link goes nowhere means either building that screen twice or shipping a dead end to real clinics. The endpoints have to exist before the screens that call them.

### Success

- A user who forgets their password receives a reset email, sets a new one, and logs in — end to end, against Mailpit locally.
- A request for an address that has no account is **indistinguishable** from one that does, in both body and latency.
- Completing a reset invalidates every refresh token the user had.
- `./mvnw test` green with Testcontainers; `ApplicationModules.verify()` still green.

## Scope

### In scope

**Endpoints** (`iam`, both public — per `api/AGENTS.md` a new route is authenticated unless deliberately added to the public list)
- `POST /auth/forgot-password` — Clinic scoped from the subdomain, exactly as login is. Never from a body field (ADR 0008).
- `POST /auth/reset-password` — consumes a token, sets the new password.

**Persistence**
- `password_reset_token`: Clinic-owned, `@TenantId` on `clinicId`, token stored as a **hash** (the pattern `staff_invitation` already uses since `V5__store_invitation_token_hash.sql`), single-use, short-lived.
- Flyway `V6` creating the table **and its RLS policy in the same migration** — ADR 0008 and `api/AGENTS.md` both require it; a Clinic-owned table without a policy is unprotected in silence.

**Mail infrastructure** (new to the product)
- `spring-boot-starter-mail` in `api/pom.xml`, driven entirely by `spring.mail.*`.
- **Mailpit** (`axllent/mailpit`, 1025 SMTP / 8025 UI) as a service in `api/compose.yaml`, wired in `application-local.yaml`.
- Production is an SMTP relay configured per environment. The provider is a value, never a dependency in code.

**Delivery via the existing outbox**
- The request publishes a domain event; an `@ApplicationModuleListener` inside `iam` sends the mail. The Modulith event-publication registry is already active with `republish-outstanding-events-on-restart: true`, so a failed send is retried rather than lost.
- No `notifications` module. One listener does not justify a module boundary; when invitation delivery and appointment reminders want the same thing there will be three real cases to shape it around.

**Account-enumeration and abuse defences**
- Identical response for known and unknown addresses. Asynchronous delivery is what keeps the *timing* identical too — a synchronous SMTP call would leak the difference no matter what the body said.
- Rate limiting on `forgot-password`.
- Reset deletes **all** `RefreshToken` rows for the user.

### Out of scope
- Any frontend. The two screens ship in `frontend-auth-shell`.
- Changing how invitations are delivered. They keep the hand-delivery pattern; migrating them onto email is a separate decision.
- Password policy, breach-list checks, MFA, account lockout after failed logins.
- Choosing the production email provider, and the DNS work (SPF/DKIM) that goes with it. Deployment concern, not a code concern.

## High-level approach

Details belong to the design phase; this is the shape.

| Area | Direction |
|---|---|
| Clinic scoping | Subdomain, identical to login. `SubdomainClinicResolutionFilter` already runs before authentication, which is what makes a public endpoint tenant-scoped at all. |
| Token at rest | Hash only, like `StaffInvitation.tokenHash`. A leaked database dump must not be a pile of usable reset links. |
| Token lifetime | Short and single-use. **The exact TTL is a design-phase decision** — the invitation's `P7D` is far too long for a credential reset, but the value is not settled here. |
| Delivery | Domain event + `@ApplicationModuleListener` in `iam`, over the existing Modulith outbox. |
| Rate limiting | Mechanism and storage are a design-phase decision; the requirement is that the endpoint is limited. |
| Errors | RFC 7807 `ProblemDetail` through the two existing paths, never hand-built in a controller. Messages as constants in `ErrorMessages`. |
| Local mail | Mailpit in `api/compose.yaml`; tests assert against it or a test double, never a real relay. |

## Risks

**The enumeration guarantee is easy to break by accident and hard to notice.** Every path out of `forgot-password` — success, unknown email, revoked membership, malformed address, rate-limited — has to converge on the same status, the same body and comparable latency. This is the one property worth an explicit test rather than trusting review.

**Email in production is not proven by this change.** Working against Mailpit says nothing about deliverability: a verified sender, SPF and DKIM, and domain reputation all fail weeks later and silently. Keeping the provider behind `spring.mail.*` is what keeps that a deployment problem instead of a code problem.

## Rollback plan

- **Code:** additive — new endpoints, a new entity, a listener, one dependency. Rollback is reverting the commits; the existing auth surface is untouched.
- **Schema:** one new table in `V6`. Rollback drops the table and its policy; nothing existing is altered, so no data is at risk.
- **Config:** the Mailpit service and `spring.mail.*` are additive. Removing them leaves the rest of the local environment working.
- **Live state:** any reset token in flight becomes unusable on rollback. Acceptable — no production deployment exists yet.

## Next step

`sdd-spec` and `sdd-design`. Spec captures Given/When/Then for request, completion, expiry, reuse, enumeration-safety and session revocation. Design settles the token TTL, the rate-limiting mechanism, and the event/listener wiring inside `iam`.
