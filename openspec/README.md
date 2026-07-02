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

- The product root (`/Users/andregallegos/Developer/zendent-app/`) is NOT a git
  repository. Do not run git commands here. The frontend package
  (`webapp-zendent/`) is its own git repo — commits for frontend changes
  happen there, and for the backend inside `api/` once it has its own repo
  or a monorepo root is established.
- Real code is authoritative over the legacy planning docs under
  `webapp-zendent/docs/plan/`. Known discrepancies: those docs describe a
  Gradle backend build and package `com.zendenta`; the actual scaffold uses
  Maven and package `com.zendent.api` (groupId `com.zendent`).
- Roadmap intent (see `webapp-zendent/docs/plan/phase-*.md`): change 1 is
  `frontend-layout-reservations` (Fase 1), change 2 is `backend-foundations`
  (Fase 2).
