# Project state (HEAD)

Compact recovery context. **Git, code, tests, and docs are authoritative — not chat history.**

Derive current commit with `git rev-parse --short HEAD` (do not treat any SHA below as permanent HEAD).

## Documentation model

| Layer | Answers | Location |
|-------|---------|----------|
| Requirements | **what** | [`requirements/`](requirements/) |
| Architecture | **how** | [`architecture/`](architecture/) |
| ADR | **why** | [`adr/`](adr/) |
| Milestones | **when** | [`milestones/`](milestones/) |
| Tests | **verification** | `mvn clean verify` + `ui/` npm test/build |
| AGENTS.md | **AI workflow** | [`../AGENTS.md`](../AGENTS.md) |

## What is this project?

**Keycloak / RHBK Operations Platform** (`keycloak-operations-mcp` + `ui/`): MCP + REST `/api/v1`, multi-target Keycloak/RHBK admin (read-oriented), OpenShift/Kubernetes inventory, health & deterministic assessments, PostgreSQL history, semantic Prometheus metrics, and a **Fleet Operations Console** (Web UI).

Repo: https://github.com/csfreitas/keycloak-operations

## Current Version

`0.7.0-SNAPSHOT` (`pom.xml` / `ui/package.json`)

Historical milestone commits (immutable):

| Milestone | Commit |
|-----------|--------|
| 0.6.1 Metrics Hardening | `089f6ea` |
| 0.6 Metrics | `59461d1` |
| 0.5 Assessment depth | `a0ffe9b` |
| 0.4 Inventory | `64a0f8c` |

## Milestone status

| | |
|--|--|
| Latest completed | **0.7** Web UI / Fleet Operations Console |
| Next (PLANNED) | **0.8** Snapshots / drift depth |
| Index | [milestones/README.md](milestones/README.md) |

## What already works

MCP + REST shared services; multi-target registry; Keycloak Admin reads; Flyway V1–V6; inventory/discovery; assessment engine + profiles; HealthCheckEngine; semantic metrics with bounds/stale/histogram semantics; performance rules; ServiceMonitor/scrape evidence when accessible; lab compose (Keycloak A/B, PostgreSQL, Prometheus); **Fleet Operations Console** (`ui/`) consuming REST/SSE only; enriched fleet/overview DTOs; health/snapshot detail endpoints; optional OIDC profile (`%oidc`); OpenShift UI manifests (no assessor SA).

## Known limitations

- Opt-in ITs skipped without `RUN_*_IT` + live stack
- ServiceMonitor requires monitoring CRD + RBAC
- Management health needs management URL when used
- Identity A OIDC disabled by default (OPEN_LAB); full browser OIDC login UX is scaffolded, production IdP wiring is operator-configured
- SSE is in-process (not multi-replica fan-out)
- VM inventory still future

## Test baseline

Recorded after 0.7 Web UI:

```bash
mvn clean verify
cd ui && npm ci && npm run test:run && npm run build
```

| | Result |
|--|--------|
| Backend unit | **129** run, 0 fail |
| Failsafe | **4** run, **4** skipped |
| Frontend | **49** Vitest tests |
| Builds | Backend + UI **SUCCESS** |

## New Agent Quick Start

1. Read [`AGENTS.md`](../AGENTS.md).
2. Read this file.
3. Read [`milestones/README.md`](milestones/README.md) → CURRENT/next milestone.
4. Read related requirements + architecture.
5. Run `mvn clean verify` (and `cd ui && npm test` when touching UI).
6. Inspect code before changing anything.
7. Implement only the agreed scope.
8. Do not commit/push without permission.
