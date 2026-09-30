# ADR-0010: Conventional Commits + SemVer

- **Status:** Accepted
- **Date:** 2026-07-09
- **Deciders:** Project owner

## Context

Without a convention, commit messages are free-form. Code review becomes
harder, and we can't auto-generate a changelog.

## Decision

We adopt:

- **Conventional Commits** for commit messages:
  `feat: ...`, `fix: ...`, `chore: ...`, `refactor: ...`, `docs: ...`,
  `test: ...`, etc. PR titles follow the same format.
- **Semantic Versioning** (`MAJOR.MINOR.PATCH`) for releases.
- **commitlint** in CI to enforce the format on PR titles.
- **release-please** (planned for Fase 10) to automate the changelog and
  version bumps.

## Rationale

- Standard tooling exists for both.
- It enables automatic CHANGELOG generation.
- The PR-title format keeps the squash-merge commit history clean.

## Consequences

- A PR titled "Fix stuff" will be rejected by the commitlint workflow.
- The main branch is protected against direct pushes; all changes come
  through PRs.

## Examples

| Type    | PR title                                     | Effect on version |
| ------- | -------------------------------------------- | ----------------- |
| `feat`  | feat(catalog): add pagination to /movies     | minor bump        |
| `fix`   | fix(payment): prevent double-charge on retry | patch bump        |
| `feat!` | feat!: rename /movies to /titles             | major bump        |
| `chore` | chore(deps): bump spring-boot to 3.5.1       | no bump           |
| `docs`  | docs(adr): add ADR-0011                      | no bump           |
