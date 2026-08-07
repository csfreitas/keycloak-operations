# Keycloak / RHBK Operations

Backend platform for **administration**, **diagnostics**, **health**, **assessment**, and **semantic metrics** of [Keycloak](https://www.keycloak.org/) and [Red Hat build of Keycloak (RHBK)](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak) environments.

| | |
|---|---|
| Version | **0.6.1-SNAPSHOT** *(experimental / evolving)* |
| Artifact | `io.github.keycloakmcp:keycloak-operations-mcp` |
| Runtime | Java **21**, Quarkus **3.38.1** |
| License | [Apache License 2.0](LICENSE) |
| Repository | https://github.com/csfreitas/keycloak-operations |

## Why it exists

Operators ask natural-language and console questions about realms, HA posture, misconfigurations, and runtime health across **many** environments. This project provides a **structured, auditable** MCP + REST backend — with redaction, target isolation, and deterministic assessments — instead of ad-hoc shell/`curl` against Admin APIs.

## Current capabilities

- Multi-target Keycloak/RHBK **read-only** Admin tools (MCP)
- REST `/api/v1` for fleet, history, inventory, assessments, health, metrics
- OpenShift/Kubernetes infrastructure inventory (target-aware)
- Deterministic assessment engine + health checks
- Semantic Prometheus / OpenShift Monitoring metrics (no raw PromQL from clients)
- PostgreSQL persistence (Flyway) for operational history — **not** a TSDB

**Not yet:** Web UI (milestone **0.7**). Status detail: [`docs/project-state.md`](docs/project-state.md).

## Architecture (summary)

```text
MCP / REST → Application Services → Target Registry
  → Keycloak / Infrastructure / Metrics providers
  → Evidence → Rules → Findings
```

See [`docs/architecture/`](docs/architecture/).

## Quick start

```bash
# Local Keycloak + PostgreSQL (+ Prometheus profile as documented)
docker compose -f dev/compose.yaml up -d

mvn quarkus:dev
# MCP Streamable HTTP typically at http://localhost:8081/mcp
```

Build / test:

```bash
mvn clean verify
```

More: [`docs/development.md`](docs/development.md).

## Documentation

| Path | Purpose |
|------|---------|
| [docs/project-state.md](docs/project-state.md) | HEAD snapshot for humans & agents |
| [docs/requirements/](docs/requirements/) | **What** (normative requirements) |
| [docs/architecture/](docs/architecture/) | **How** |
| [docs/adr/](docs/adr/) | **Why** (decisions) |
| [docs/milestones/](docs/milestones/) | **When** (delivery slices) |
| [docs/roadmap.md](docs/roadmap.md) | Compact roadmap |
| [AGENTS.md](AGENTS.md) | AI coding bootstrap |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guide |
| [docs/development/ai-assisted-development.md](docs/development/ai-assisted-development.md) | AI-assisted development policy |

Topic catalogs (tools, REST, rules, evidence, UI concepts) remain under [`docs/`](docs/).

## Roadmap

0.1–0.6.1 ✅ → **0.7 Web UI (next)** → 0.8 drift → 0.9 schedules/alerts → 1.0.
Details: [`docs/roadmap.md`](docs/roadmap.md).

## Status

This repository is under active development (`*-SNAPSHOT`). APIs and tools may change. Compatibility claims distinguish **tested** vs **design-compatible** — see [`docs/compatibility.md`](docs/compatibility.md).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). AI-assisted contributions are welcome; contributors remain responsible for correctness, tests, and review.
