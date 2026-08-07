# Roadmap

The project is evolving from a **Keycloak MCP Server** into a **Keycloak / RHBK Operations Platform** with four capability surfaces:

1. Administration (read-oriented Admin API tools + REST)
2. Assessment (rules, findings, history)
3. Observability (semantic metrics + future Prometheus/Thanos)
4. AI / MCP (same application services as REST)

## 0.1 — Keycloak admin read-only

- Streamable HTTP MCP on Quarkus (+ optional STDIO)
- Read-only Admin API tools (realms, clients, users, groups, roles, server info)
- Sensitive data filtering and audit logging
- Product / capability detection (Keycloak vs RHBK)

## 0.2 — Multi-target

- Target registry (`targetId` only; no arbitrary URLs from the LLM)
- Credential refs + `CredentialProvider` (secrets never on Target)
- Dual-Keycloak local lab + isolation tests

## 0.3 — PostgreSQL, persistence, audit, history *(current)*

- PostgreSQL + Flyway + Hibernate Panache
- Assessment / findings / health / audit / environment snapshots
- REST API `/api/v1` sharing Application Services with MCP
- Fleet + target overview APIs
- `MetricsProvider` abstraction (semantic queries; no raw PromQL tools)
- UI architecture docs for a future `keycloak-operations-ui`

## 0.4 — OpenShift / Kubernetes discovery

- Evidence collectors (deployments, routes, PDBs, topology)
- Richer environment snapshots from live cluster state

## 0.5 — Health check + assessment engine depth

- Wire collectors into assessment profiles
- Stronger HA / security / capacity rules with real evidence

## 0.6 — Prometheus / metrics integration

- Live `PrometheusMetricsProvider` against Target observability bindings
- Compatibility path for Thanos / OpenShift Monitoring / Mimir
- Semantic MCP tools (`keycloak_get_request_rate`, latency, JVM, DB pool, …)

## 0.7 — Web UI / Fleet dashboard

- Separate `keycloak-operations-ui` (React/TypeScript)
- OIDC (Identity A) for operators; never reuse Target admin credentials
- Fleet, overview, findings, history screens over REST + SSE events

## 0.8 — Snapshots, historical comparison, configuration drift

- Change detection UX and MCP tools (`keycloak_get_environment_changes`)
- Multi-target comparison (HML vs PRD)

## 0.9 — Scheduled assessments, alerts, notifications

- Quarkus Scheduler + HA coordination (distributed locking / clustered jobs)
- Retention enforcement jobs
- Alert hooks on CRITICAL findings / health transitions

## 1.0 — Production-ready Operations Platform

- Stable REST + MCP contracts
- Documented Keycloak **and** RHBK CI coverage
- Hardened OpenShift defaults
- Clear health-check vs assessment UX

Dates beyond the current milestone are not promised; later items are planned scope.
