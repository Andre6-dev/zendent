# Domain Docs

How engineering skills consume this repository’s domain documentation.

## Before exploring

- Read `CONTEXT.md` at the repository root.
- Read ADRs under `docs/adr/` that affect the area being changed.
- If either location is absent, proceed silently.

## Layout

This is a single-context repository:

```text
/
├── CONTEXT.md
├── docs/adr/
├── api/
└── webapp-zendent/
```

## Vocabulary

Use the domain terms defined in `CONTEXT.md` in code, tests, issues and design documents. Avoid synonyms that the glossary explicitly rejects.

If a required concept is missing, reconsider whether the project already has a suitable term or record the gap for `domain-modeling`.

## ADR conflicts

If proposed work contradicts an existing ADR, surface the conflict explicitly instead of silently overriding the decision.
