# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`
- **Read an issue**: `gh issue view <number> --comments`.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments`.
- **Comment**: `gh issue comment <number> --body "..."`
- **Apply or remove labels**: `gh issue edit <number> --add-label "..."` or `--remove-label "..."`.
- **Close**: `gh issue close <number> --comment "..."`.

Infer the repository from `git remote -v`; `gh` does this automatically inside the clone.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub shares one number space across issues and pull requests. Resolve ambiguous references using `gh pr view <number>` and fall back to `gh issue view <number>`.

## Publishing and fetching

When a skill says “publish to the issue tracker”, create a GitHub issue.

When a skill says “fetch the relevant ticket”, run:

```bash
gh issue view <number> --comments
```

## Wayfinding operations

- A map is an issue labelled `wayfinder:map`.
- Child tickets use GitHub sub-issues when available.
- Dependencies use GitHub native issue dependencies.
- A ticket is available only when all blockers are closed and it has no assignee.
- Claim a ticket with `gh issue edit <number> --add-assignee @me`.
- Resolve it by commenting with the outcome and closing the issue.
