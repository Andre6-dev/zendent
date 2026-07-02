# Design: Frontend Layout + Reservations (mock) — Phase 1

## Technical Approach

A pathless `_app` shell (Sidebar + Navbar) wraps every business route via `<Outlet/>`; `/` redirects to `/reservations`. The Reservations screen renders a resource Day view (dentists x hours) from mock data behind a TanStack Query seam. HeroUI v3 is used CSS-first (no provider). The calendar is a custom CSS grid with absolute-positioned blocks. The live clock is read client-only to stay hydration-safe. Data shape is locked with Zod so a later change swaps mock -> API by editing only `queryFn` bodies.

## Architecture Decisions

### Decision: No `HeroUIProvider`
**Choice**: Do NOT wrap the app in any HeroUI provider.
**Alternatives considered**: Wrap `__root.tsx` per the legacy plan (a HeroUI v2 mindset).
**Rationale**: The `heroui-react-pro` skill states plainly "No Provider needed — components work directly without `<HeroUIProvider>`"; v3 is CSS-first and `styles.css` already imports `@heroui/styles` + `@heroui-pro/react/css`. Verified against code: `__root.tsx` renders no provider and components (`Button`, `Avatar`, `Spinner`, `Sheet`) work. Overlays self-portal into the document; navigation uses TanStack Router `Link` directly (no provider-level link integration). No theme-switch or toast surface exists in Phase 1. This **supersedes** the proposal's "HeroUIProvider in `__root.tsx`" line. If toast/theme-switching arrives later, add a scoped provider at that point — not now.

### Decision: Custom CSS-grid calendar
**Choice**: Custom `CalendarGrid` with absolute-positioned blocks; no HeroUI Pro Calendar, no FullCalendar.
**Alternatives considered**: HeroUI Pro `Calendar`/`DatePicker`; FullCalendar resource-timeline.
**Rationale**: The Pro component catalog (Navigation, Data Display) ships no resource-timeline; `Calendar`/`DatePicker` are month/date pickers, not a dentists-x-hours grid. Mechanism (as built): one scroll container; a `sticky top-0` header row (timezone cell + `DentistColumnHeader`s); body height = `VISIBLE_HOURS * HOUR_HEIGHT`. Every appointment/break/unavailable block is placed by pure helpers in `calendar.ts` — `topForDate(start)` and `heightForSpan(start, end)`. Dentist columns are flex; blocks are `absolute` inside each `relative` column. Keeps zero new dependencies (proposal constraint).

### Decision: Hydration-safe clock
**Choice**: Client-only deferred time read; no `suppressHydrationWarning`.
**Alternatives considered**: Read `new Date()` during render (hydration mismatch); blanket `suppressHydrationWarning`.
**Rationale**: `NowIndicator` initializes `now = null`, then sets the real `Date` in `useEffect` (plus a 60s interval). Server render and first client render both emit `null` -> identical markup -> no mismatch, so no warning suppression is needed. `selectedDate` uses a lazy `useState(() => new Date())` so the clock is read once at mount, never during SSR module eval.

### Decision: Routing + Query seam
**Choice**: `_app` pathless layout route; one file route per sidebar destination; `nav-items.ts` as the single nav source typed against `routeTree.gen`; `queries.ts` `queryOptions` with stable keys.
**Rationale**: Typed routes give compile-time safety (`AppRoute = FileRouteTypes['to']`). Non-`/reservations` routes render `PlaceholderPage`. The seam keeps `queryKey`s (`['dentists']`, `['reservations', dayKey]`) and component contracts fixed; Phase 3 swaps only the `queryFn` bodies.

### Decision: Theming/tokens
**Choice**: Single `--accent` override (royal blue-indigo oklch) in `styles.css`; rely on HeroUI semantic tokens otherwise.
**Rationale**: Minimal surface, matches design-taste guidance. **FLAG (fix)**: brand is "Zendent" but `__root.tsx` `title` = `'Zendenta'` and comments in `styles.css` / `nav-items.ts` say "Zendenta" — correct to "Zendent".

## Data Flow

```
route /_app/reservations
  useQuery(dentistsQueryOptions)  ──► mocks/dentists  ─┐
  useQuery(dayScheduleQueryOptions) ─► mocks/reservations ─┤ Zod-validated
        │ (state: date, view, dentistFilter)              │
        ▼                                                  ▼
  CalendarToolbar        CalendarGrid ──► DentistColumn ──► AppointmentCard
                              └► TimeAxis  └► NowIndicator (client-only)
```

## Sequence: initial render -> hydration -> now-indicator

```
Server         Client(hydrate)        Effect (post-mount)
  │ render grid   │ same markup          │
  │ now=null      │ now=null (match)     │ setNow(new Date())
  │ no red line   │ no red line          │ ► red line appears, ticks 60s
```

## File Changes (as built — verify, don't recreate)

| Path | Action | Description |
|------|--------|-------------|
| `src/routes/__root.tsx` | Modify | Set `title`/`lang` to Zendent; NO provider |
| `src/routes/index.tsx` | Verify | `/` -> `/reservations` redirect |
| `src/routes/_app/route.tsx` + destinations | Verify | Pathless shell + per-item routes |
| `src/components/layout/*` | Verify | AppLayout, Sidebar, Navbar, ClinicSwitcher, nav-items |
| `src/components/reservations/*` | Verify | Toolbar, Grid, TimeAxis, DentistColumn, AppointmentCard, NowIndicator |
| `src/features/reservations/*` | Verify | schemas, types, calendar helpers, queries, status |
| `src/mocks/*` | Verify | faker dentists + day schedule |
| `src/styles.css` | Modify | Fix "Zendenta" comment -> "Zendent" |

## Interfaces / Contracts

```ts
dentistsQueryOptions(): queryOptions // key ['dentists']
dayScheduleQueryOptions(dayKey: string): queryOptions // key ['reservations', dayKey]
topForDate(date): number; heightForSpan(start, end): number // px, min 28
```

## Testing Strategy

| Layer | What | Approach |
|-------|------|----------|
| Unit | `calendar.ts` helpers (top/height/dayKey/isSameDay) | vitest pure fns |
| Unit | Zod schemas accept faker output | parse in mocks test |
| Smoke | Layout + Reservations render, no hydration error | existing `*.smoke.test.tsx` |

## Migration / Rollout

No migration. Additive frontend-only; rollback = delete added files + revert `__root.tsx`/`styles.css` edits.

## Open Questions

- [ ] Confirm week-view toggle stays inert (shows "coming soon") for Phase 1.
