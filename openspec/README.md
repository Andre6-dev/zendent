# openspec — zendent-app

This directory is the Spec-Driven Development (SDD) artifact store for the
**zendent-app** product. It lives at the product root (above `webapp-zendent/`
and `api/`) because changes in this product are frequently cross-cutting
between the frontend and backend.

## Layout

```
openspec/
├── config.yaml       # project context, phase rules, test/build commands
├── specs/            # source-of-truth specs, merged from change deltas on archive
└── changes/          # active and archived changes
    ├── archive/      # completed changes (YYYY-MM-DD-{change-name}/)
    └── {change-name}/
```

## Notes

- The product root IS the git repository, and the only one. `webapp-zendent/`
  and `api/` are directories inside it, not nested repos or submodules — see
  ADR 0016. An earlier version of this note claimed the opposite and was the
  source of the confusion behind the `backend-foundations` git gate (task 0.1).
  Run git commands from the product root.
- Real code is authoritative over the legacy planning docs under
  `webapp-zendent/docs/plan/`. Known discrepancies: those docs describe a
  Gradle backend build and package `com.zendenta`; the actual scaffold uses
  Maven and package `com.zendent.api` (groupId `com.zendent`).
- Roadmap intent (see `webapp-zendent/docs/plan/phase-*.md`): change 1 was
  `frontend-layout-reservations` (Fase 1) and change 2 was `backend-foundations`
  (Fase 2). Both are archived; their specs are merged into `specs/`. Phase 3 was
  then re-planned (see `webapp-zendent/docs/plan/phase-3-core-modules.md`): two
  prerequisites come before the business modules, because the frontend has no
  integration with the Fase 2 auth surface at all. The order is
  `iam-password-recovery` → `frontend-auth-shell` → the Staff List, then
  `patients`, `treatments` and `reservations` against the real API.
