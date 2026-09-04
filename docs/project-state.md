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

**Keycloak / RHBK Operations Platform** (`keycloak-operations-mcp` + `ui/`): MCP + REST `/api/v1`, multi-target Keycloak/RHBK admin, OpenShift/Kubernetes inventory, health & deterministic assessments, PostgreSQL history, semantic Prometheus metrics, Fleet Operations Console (Web UI), and **controlled administration** (plan → approve → apply → verify).

Repo: https://github.com/csfreitas/keycloak-operations

## Current Version

`0.8.0-SNAPSHOT` (`pom.xml` / `ui/package.json`)

Historical milestone commits (immutable):

| Milestone | Commit |
|-----------|--------|
| 0.7 Web UI | `610e444` |
| 0.6.1 Metrics Hardening | `9ebadc9` |
| 0.6 Metrics | `81eff56` |
| 0.5 Assessment depth | `c0d00a3` |
| 0.4 Inventory | `4d01a9a` |

## Milestone status

| | |
|--|--|
| Latest completed | **0.8** Controlled Administration & Change Management |
| Current (IN PROGRESS) | **0.8.1** Realm & Client Administration — Slices 1–2 |
| Index | [milestones/README.md](milestones/README.md) |

## What already works

MCP + REST shared services; multi-target registry; Keycloak Admin reads + controlled writes via change lifecycle; Flyway V1–V7; inventory/discovery; assessment engine + profiles; HealthCheckEngine; semantic metrics; performance rules; lab compose; Fleet Operations Console with Changes views; optional OIDC profile (`%oidc`); OpenShift UI manifests.

**0.8 foundation:** ChangeRequest/ChangePlan lifecycle, safe diff, risk, environment policy, approval bound to plan fingerprint, apply with stale-plan protection, read-back verification, audit, semantic MCP/REST change tools, proof-of-concept non-sensitive client config update (`name` / `description` / `pkceCodeChallengeMethod`).

**0.8.1 Slice 1:** typed replacement of client redirect URI and Web Origin sets; deterministic validation/normalization; item-level set diff; structured fingerprint/persistence with legacy scalar compatibility; transition-aware risk/policy; stale-plan protection; secret-cleared apply; read-back verification; shared REST `POST /changes/plan/client-urls` and MCP `keycloak_plan_update_client_urls` surfaces.

**0.8.1 Slice 2:** typed PKCE, Authorization Code, Implicit, Direct Access Grants, service-account, and public/confidential client settings; effective-combination validation; transition-aware risk and production denial; stale-plan protection; secret-cleared apply; read-back verification; shared REST `POST /changes/plan/client-security` and MCP `keycloak_plan_update_client_security` surfaces.

**Operations report foundation (working tree):** shared `OperationsReportService` combines sanitized snapshots, health, deterministic assessments/findings, and optional semantic metrics. REST `POST /targets/{targetId}/operations-reports` returns the complete structured report and Markdown; MCP `keycloak_generate_operations_report` returns a compact agent-oriented envelope and Markdown. Section completeness is explicit and independent from target health. See [operations-reporting.md](architecture/operations-reporting.md) and [ADR 0008](adr/0008-ai-assists-backend-decides.md).

## Known limitations

- Opt-in ITs remain skipped without `RUN_*_IT` and an explicitly provisioned live stack
- Client lifecycle and realm administration remain incomplete in 0.8.1; broad administration remains deferred behind fleet reporting/onboarding and authorization work
- Slice 2 client security/flow writes are verified against disposable Community Keycloak 26.7.1; RHBK writes remain `NOT VERIFIED`
- Exact client redirect URI/Web Origin writes are verified against disposable Community Keycloak 26.7.1; Web Origin `+` behavior and RHBK writes remain `NOT VERIFIED`
- Destructive ops, password/secret workflows out of scope
- SSE is in-process (not multi-replica fan-out)
- Identity A OIDC disabled by default (OPEN_LAB)
- VM and Docker inventory collectors are not implemented; only OpenShift/Kubernetes have detailed infrastructure inventory
- `mcp.read-only=true` by default; apply requires explicit opt-out + WRITE

## Test baseline (after 0.8)

```bash
mvn clean verify
cd ui && npm run test:run && npm run build
```

| | Result |
|--|--------|
| Backend unit | **153** run, 0 fail |
| Failsafe | **5** run, **5** skipped |
| Frontend | **50** Vitest tests |
| Builds | Backend + UI **SUCCESS** |

The table above is the accepted 0.8 baseline. Current 0.8.1 working-tree validation must be reported separately until the full backend, UI, and opt-in integration commands have been executed in a suitable environment.

Current working-tree validation:

| Check | Result |
|---|---|
| Operations report unit/delegation tests | **7 passed** |
| UI Vitest | **51 passed** |
| UI production build | **SUCCESS** |
| Full Java 21 `mvn clean verify` | **192 passed**, 0 failed; **7 opt-in ITs skipped** without flags |
| Community Keycloak 26.7.1 controlled-write opt-in IT | **3 passed**: URL apply/read-back/restore, stale-plan rejection, and Slice 2 security/flow apply/read-back/restore |
| Disposable stack | PostgreSQL, Keycloak 26.7.1, and Prometheus were **HEALTHY** during validation and were removed afterward |
| MCP smoke / Operations Report | **PASS**; report `PARTIAL`, health `UNKNOWN`, assessment `PARTIAL`, metrics `AVAILABLE` for the intentionally infrastructure-free lab target |
| RHBK | **NOT VERIFIED** |

The community CI workflow runs on pushes to `main` and pull requests. It performs
the real opt-in Community Keycloak 26.7.1 integration tests before the read-only
MCP and operations-report smoke path.

Local validation handoff:
[`development/local-validation-0.8.1-operations-report.md`](development/local-validation-0.8.1-operations-report.md).

## New Agent Quick Start

1. Read [`AGENTS.md`](../AGENTS.md).
2. Read this file.
3. Read [`milestones/README.md`](milestones/README.md) → CURRENT/next milestone.
4. Read related requirements + architecture (for writes: [`architecture/controlled-administration.md`](architecture/controlled-administration.md)).
5. Run `mvn clean verify` (and `cd ui && npm test` when touching UI).
6. Inspect code before changing anything.
7. Implement only the agreed scope.
8. Do not commit/push without permission.
