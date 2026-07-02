# Proposal: Frontend Layout + Reservations (mock) — Phase 1

## Intent

Ship Zendent's first visual deliverable: a navigable app shell (sidebar + navbar) plus a Reservations screen rendering a resource calendar (dentists x hours) from mock data. Goal is to validate the UX against the mockup NOW, with zero backend risk. Data shape is locked via Zod schemas so a later change swaps mock -> API without touching components.

## Scope

### In Scope
- Providers wiring: `HeroUIProvider` in `src/routes/__root.tsx`, brand title/logo "Zendent", `lang`.
- App layout: grouped sidebar (CLINIC / FINANCE / PHYSICAL ASSET + Report, Customer Support), navbar (global search, "+" placeholder, notifications, profile), static `ClinicSwitcher` ("Avicena Clinic").
- Typed route tree: real TanStack Router routes for EVERY sidebar destination; `/reservations` functional, all others render a "Coming soon" placeholder.
- Mock layer: Zod schemas + faker generators (~3-5 dentists, 12-16 appointments 9am-5pm, varied statuses, one break block, one "Not available" column).
- Reservations Day view: `CalendarToolbar`, resource `CalendarGrid`, `TimeAxis`, `DentistColumn`, `AppointmentCard`, `NowIndicator`, empty-cell "+" placeholder. Client-side dentist filter.

### Out of Scope
- Any backend, persistence, or real API.
- Functional new-appointment modal (visual "+" only).
- Week view (toggle shown, inactive / "coming soon").
- Functional clinic switching (static label only).
- Real content for placeholder pages (later phases swap in).

## Capabilities

### New Capabilities
- `app-shell`: providers, layout (sidebar + navbar + ClinicSwitcher), typed routes, placeholder pages, `/` -> `/reservations` redirect.
- `reservations-calendar`: resource Day-view calendar with toolbar, grid, now-indicator, appointment/break/unavailable rendering, client-side filters.
- `reservations-mock-data`: Zod schemas + faker generators as source-of-truth data shape (reused when backend arrives).

### Modified Capabilities
- None.

## Approach

Slice per the plan's PKG breakdown: **1.1** Base & Providers (blocks all) -> **1.2** Layout + **1.3** Mock & types (parallel) -> **1.4** Calendar -> **1.5** Polish. Evaluate HeroUI Pro's Calendar first; if it lacks a resource-timeline view, build a custom CSS-grid `CalendarGrid` (the default path). Do NOT install FullCalendar.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| Frontend only (`webapp-zendent/`) | New/Modified | All work here; backend untouched |
| `src/routes/__root.tsx` | Modified | Add HeroUIProvider, title, lang |
| `src/routes/_app/**` | New | Layout route + every sidebar route |
| `src/components/{layout,reservations}/**` | New | Shell + calendar components |
| `src/features/reservations/**`, `src/mocks/**` | New | Schemas, queries, faker mocks |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| SSR hydration mismatch (client date/time in grid + now-indicator) | Med | Mark grid/now-indicator client-only, hydration-safe |
| HeroUI Pro Calendar lacks resource-timeline | High | Fallback custom CSS-grid; no FullCalendar |
| HeroUI Pro is beta (`1.0.0-beta.6`) API drift | Med | Pin versions |
| Provider necessity: skill says v3 needs no `HeroUIProvider` | Low | Verify in spec/design; keep wrapping per plan if required |

## Rollback Plan

Trivial and additive. All work is new files (routes/components/mocks/features) plus provider wrapping in `__root.tsx`. Revert = delete the added files and remove the provider/title/lang edits in `__root.tsx`; no data, migrations, or existing behavior affected. Affected side: **frontend only**.

## Dependencies

- Existing HeroUI styles already imported in `src/styles.css`.
- `@faker-js/faker`, TanStack Router/Query, Zod (already installed).

## Success Criteria

- [ ] `bun run dev` -> `/` redirects to `/reservations`; every sidebar item routes (placeholder or live).
- [ ] Reservations Day view matches mockup: dentist columns, time axis, status-colored cards, break + "Not available", red now-indicator.
- [ ] Client filters (dentist, Today) operate; Week shows as inactive.
- [ ] No hydration errors; `bun run lint` and `bun run build` pass.
