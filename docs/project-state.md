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
| Tests | **verification** | `mvn clean verify` |
| AGENTS.md | **AI workflow** | [`../AGENTS.md`](../AGENTS.md) |

## What is this project?

**Keycloak / RHBK Operations Platform** backend (`keycloak-operations-mcp`): MCP + REST `/api/v1`, multi-target Keycloak/RHBK admin (read-oriented), OpenShift/Kubernetes inventory, health & deterministic assessments, PostgreSQL history, semantic Prometheus metrics. Web UI is planned as **0.7**.

Repo: https://github.com/csfreitas/keycloak-operations

## Current Version

`0.6.1-SNAPSHOT` (`pom.xml`)

Historical milestone commits (immutable):

| Milestone | Commit |
|-----------|--------|
| 0.6 Metrics | `59461d1` |
| 0.5 Assessment depth | `a0ffe9b` |
| 0.4 Inventory | `64a0f8c` |

## Milestone status

| | |
|--|--|
| Latest completed | **0.6.1** Metrics Hardening |
| Next (PLANNED) | **0.7** Web UI |
| Index | [milestones/README.md](milestones/README.md) |

## What already works

MCP + REST shared services; multi-target registry; Keycloak Admin reads; Flyway V1–V6; inventory/discovery; assessment engine + profiles; HealthCheckEngine; semantic metrics with bounds/stale/histogram semantics; performance rules (incl. p95/GC/DB critical/cluster); ServiceMonitor/scrape evidence when accessible; lab compose (Keycloak A/B, PostgreSQL, Prometheus).

## Known limitations

- Opt-in ITs skipped without `RUN_*_IT` + live stack
- ServiceMonitor requires monitoring CRD + RBAC
- Management health needs management URL when used
- **No Web UI**
- SSE events stub
- VM inventory still future

## Test baseline

Recorded after 0.6.1 hardening:

```bash
mvn clean verify
```

| | Result |
|--|--------|
| Unit | **126** run, 0 fail, 0 error, 0 skipped |
| Failsafe | **4** run, **4** skipped |
| Build | **SUCCESS** |

## New Agent Quick Start

1. Read [`AGENTS.md`](../AGENTS.md).
2. Read this file.
3. Read [`milestones/README.md`](milestones/README.md) → CURRENT/next milestone.
4. Read related requirements + architecture.
5. Run `mvn clean verify`.
6. Inspect code before changing anything.
7. Implement only the agreed scope.
8. Do not commit/push without permission.
