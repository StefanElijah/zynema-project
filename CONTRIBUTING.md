# Contributing to Zynema

> Internal team conventions for the zynema-project monorepo.

## Workflow

1. Branch off `develop`: `git checkout -b feature/<short-name> develop`
2. Make atomic commits. Use [Conventional Commits](https://www.conventionalcommits.org/).
3. Push and open a PR to `develop`.
4. CI must be green. Reviewer required for merge.
5. Squash-merge with a Conventional Commit message.

## Commit message format

```
<type>(<scope>): <subject>

<body>

<footer>
```

| Type       | Use for                             |
| ---------- | ----------------------------------- |
| `feat`     | New feature                         |
| `fix`      | Bug fix                             |
| `chore`    | Tooling, deps, config               |
| `docs`     | Documentation only                  |
| `refactor` | Code change without behavior change |
| `test`     | Adding or fixing tests              |
| `perf`     | Performance improvement             |
| `ci`       | CI/CD changes                       |
| `build`    | Build system changes                |

**Examples:**

```
feat(catalog): add movie search endpoint
fix(playback): resolve NPE when manifest is missing
chore(frontend): upgrade vite to 5.4
docs(adr): record decision to use Keycloak from day 1
```

## Local environment

```bash
make check    # verify tools
make env      # copy .env.example → .env
pnpm install  # install frontend deps
make up       # boot core + auth stack
```

## Backend (Java)

- **Language:** Java 21
- **Build:** Maven (multi-module)
- **Style:** Google Java Style + 4-space indent (enforced by Prettier for Java)
- **Tests:** JUnit 5 + Testcontainers
- **Coverage floor:** 70% per module (JaCoCo)

Run from the `backend/` folder:

```bash
mvn -B -DskipTests clean install   # build all modules
mvn -B test                       # run all tests
mvn -B -pl catalog-service spring-boot:run  # run one service
```

## Frontend (TypeScript)

- **Build:** Vite 5
- **Style:** ESLint + Prettier (configured in `frontend/`)
- **Tests:** Vitest + React Testing Library + Playwright
- **Coverage floor:** 60% (Vitest)

Run from the repo root:

```bash
pnpm dev:frontend        # Vite dev server
pnpm test:frontend       # unit + component
pnpm test:e2e            # Playwright
```

## Pull request checklist

- [ ] Branch is up to date with `develop`
- [ ] `pnpm lint` passes
- [ ] `pnpm test` passes
- [ ] `mvn -B verify` passes (backend changes)
- [ ] No new `SonarQube` issues
- [ ] Conventional Commit messages
- [ ] PR description explains _why_, not just _what_

## Architectural changes

Any change that affects cross-service contracts, persistence topology, or messaging topology **must** be preceded by an ADR in `docs/adr/`. Use the template `docs/adr/template.md`.
