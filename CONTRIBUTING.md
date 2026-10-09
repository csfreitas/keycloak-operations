# Contributing

Thanks for contributing to **Keycloak / RHBK Operations** (`keycloak-operations-mcp`).

## Prerequisites

- **Java 21**
- **Maven 3.9+** (wrapper not required if system Maven is available)
- **Node.js 24 LTS** and npm recommended (for `ui/`; supported engines in `ui/package.json`)
- Docker or Podman for disposable PostgreSQL tests and optional live Keycloak/Prometheus fixtures

## Build & test

```bash
JENV_VERSION=21 jenv exec mvn clean verify
cd ui && npm ci && npm run test:run && npm run build
```

This is the default validation gate (backend + frontend). Opt-in integration tests may require environment flags and live dependencies — see [`integration-tests/README.md`](integration-tests/README.md) and [`docs/compatibility.md`](docs/compatibility.md). Do not mark a version as tested if the IT was skipped.

Without jenv, select Java 21 explicitly. Documentation-only work uses source/contract review and Markdown/link validation rather than starting runtime fixtures; state that application tests were not rerun.

## Development workflow

1. Read [`docs/project-state.md`](docs/project-state.md) and the relevant milestone under [`docs/milestones/`](docs/milestones/).
2. Prefer requirements ([`docs/requirements/`](docs/requirements/)) and architecture ([`docs/architecture/`](docs/architecture/)) over ad-hoc prompts.
3. Implement the agreed scope; share logic between MCP and REST via application services.
4. Add/update tests for behavior you change — especially isolation and security.
5. Reconcile architecture/operator guides, requirements, current milestone acceptance, project-state, CHANGELOG Unreleased and artifact-version status together; see [release bookkeeping](docs/development/release-versioning.md).
6. Run `mvn clean verify` before opening a PR.

## Documentation expectations

| Layer | Location |
|-------|----------|
| What | `docs/requirements/` |
| How | `docs/architecture/`, topic docs under `docs/` |
| Why | `docs/adr/` |
| When | `docs/milestones/` |
| AI workflow | `AGENTS.md`, `docs/development/ai-assisted-development.md` |

Keep temporary Agent prompts out of public product docs (local `.agent-prompts/` is gitignored).

## Security expectations

- Read-only by default for operational tools
- No arbitrary URLs, shell, kubectl/oc, or raw PromQL from clients
- No secrets in responses, logs, commits, or examples (use placeholders / env vars)
- Preserve multi-target isolation

## Pull requests

- Describe intent and linked milestone/requirement IDs when relevant
- Include test evidence (`mvn clean verify` summary)
- Keep PRs focused; avoid unrelated refactors
- Human review is required

AI-assisted contributions are welcome, but contributors remain responsible for correctness, testing, and review.
