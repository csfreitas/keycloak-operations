# Health checks

Lightweight checks answer “is the target reachable / basically alive?” — not
“is it production-ready?”. Full production readiness is covered by
[assessment-profiles.md](assessment-profiles.md) and the Assessment Engine.

## What runs

`HealthCheckEngine` runs all registered `HealthCheck` beans, including:

| Check | Meaning |
|-------|---------|
| Admin API | Keycloak Admin API reachability |
| Management | Optional management `/health` when `management-url` is set |
| Workload / pods / infra | Inventory-backed signals when infrastructure is configured |

Each result stores `duration_ms` (V6 schema). Overall severity order:
CRITICAL > WARNING > UNKNOWN > HEALTHY (UNKNOWN alone does not force CRITICAL).

## MCP / REST

| Surface | Call |
|---------|------|
| MCP | `keycloak_health_check` |
| REST | `POST /api/v1/targets/{targetId}/health-checks` |
| History | `GET /api/v1/targets/{targetId}/health-checks` |

## Config

```properties
health.pods.restart-warning-threshold=3
health.management.connect-timeout-ms=3000
health.management.read-timeout-ms=5000
```

## Assessment vs health

| Concern | Question |
|---------|----------|
| Health | Is Admin API up? Is infra configured? |
| Assessment | HA, security, capacity, production config posture |
