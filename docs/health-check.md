# Health checks

Lightweight checks answer “is the target reachable / basically alive?” — not
"is it production-ready?". [Assessment profiles](assessment-profiles.md) evaluate implemented posture rules, not universal production readiness. Neither subsystem certifies the environment.

## What runs

`HealthCheckEngine` runs all registered `HealthCheck` beans, including:

| Check | Meaning |
|-------|---------|
| Admin API | Server-info endpoint observation, with access/authentication/metadata gaps distinguished from request failure |
| Management | Optional configured `/health/ready`, `/health/live` and `/health` observations |
| Workload / pods / infra | Inventory-backed signals when infrastructure is configured |

Each result stores `duration_ms` (V6 schema). Overall severity order:
CRITICAL > WARNING > UNKNOWN > HEALTHY (UNKNOWN alone does not force CRITICAL).
Aggregation is independent of check order: a healthy component cannot hide an
unknown component, while a directly observed critical condition remains critical.
An unconfigured optional component contributes UNKNOWN, not an outage.

The engine converts an unexpected check exception or missing result to UNKNOWN
with a stable reason code. It does not return or log that exception's message or
cause. A collection failure means the platform could not establish the component
state; it does not prove that Keycloak itself is down.

### Admin metadata classification

The check does not broaden credentials or fall back to unrestricted reads. A non-null server-info response establishes that endpoint's reachability (HEALTHY), even if system/version metadata is absent; `versionAvailable=false` is explicit and no null version is inserted into immutable result details. This is not proof of full environment health. A null response, unsupported metadata, authorization denial, authentication failure or unclassified check error is UNKNOWN with a stable `reasonCode`. Adapter-reported transport/upstream request failure remains CRITICAL for this check (`REQUEST_FAILED`), without claiming a complete Keycloak outage. Provider exception messages, causes and response bodies are not copied into this component result.

### Management response boundary

The management URL comes only from the registered target's `keycloak.managementUrl`
or `management-url` tag. The provider accepts HTTP(S) endpoints without URL
userinfo, query or fragment, uses default TLS validation, and never follows
redirects. It returns no configured URL, response body, exception text or upstream
check names/data.

Each of the three fixed paths has a bounded result: HTTP status when received,
normalized health status, a fixed reason code when needed, and optional nested
check counts grouped by normalized status. Recognized UP/OK/HEALTHY,
WARNING/DEGRADED and DOWN/CRITICAL values map to the corresponding platform
statuses. Any recognized nested DOWN is critical, not only a database-named check.

Empty, malformed, missing-status or unknown-status responses cannot become
HEALTHY. Missing endpoints, access denial, redirects, request failures and
timeouts are UNKNOWN. A valid DOWN body on HTTP 503 remains CRITICAL; an UP or
WARNING body on HTTP 503 is a conflict and remains UNKNOWN. An arbitrary HTTP 5xx
body is not, on its own, proof of an application outage.

Responses are limited to **64 KiB before parsing**, including chunked bodies.
Strict JSON parsing rejects duplicate fields and trailing documents, limits
nesting to 16, string values to 4,096 characters and number length to 128;
at most 100 nested checks are summarized. Limit failures remain explicit rather
than silently truncating a response into a favorable result.

### Infrastructure evidence prerequisites

The infrastructure API component uses the shared inventory completeness policy,
including same-target confirmed discovery and observed API coverage. It exposes
`collectionComplete`; an unknown runtime, missing version or legacy missing coverage
is UNKNOWN even without warnings. A resolved transport client alone is not proof
of endpoint reachability. Complete evidence can establish API reachability, not
healthy workload configuration or environment-wide health. See the
[capability slice](development/h1-capability-coverage-2026-09-19.md).

Infrastructure, workload and pod checks distinguish unavailable inventory from
observed zero counts. Missing workload identity, unknown deployment method,
negative required replica/restart counts or relevant collection warnings make the affected health check
UNKNOWN. Raw inventory warning messages are not copied into these health
results. Observed no-ready-replica, OOM and restart conditions retain their
existing critical/warning classifications when the required evidence is present.

These changes apply to newly executed checks; persisted historical runs are not
rewritten. The [failure/bounds ledger](development/h1-failure-bounds-2026-09-18.md)
records the local validation scope. The broader `InventoryService` warning output
and `InfrastructureEvidenceMapper` remain separate review items; this health
projection does not claim that all inventory/evidence output is hardened.

## MCP / REST

Health metadata is filtered after the engine calculates statuses/counts and before
new summary/component persistence. Historical detail reads filter copies of names,
messages, nested details and summary; stored rows and canonical run/target IDs stay
unchanged. Unknown states, numeric facts, timestamps, durations and target-filtered
events retain their semantics. Registered check names in internal engine logs remain
separate from this output/persistence boundary; built-in names are fixed. See the
[read-metadata validation](development/h1-read-metadata-2026-09-19.md).

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
collection.operation-timeout-ms=30000
```

Health checks share the caller's remaining collection budget (or open a 30-second
scope). Management header/body time uses one per-path deadline, capped by the shared
remaining time. Later paths/checks do not start after expiry or interruption.
Unexecuted/late checks become UNKNOWN with `collectionComplete=false` and a fixed
budget reason. Earlier validated WARNING/CRITICAL observations survive explicit
partial results; incomplete collection cannot invent a healthy outcome. Report
health sections expose this partiality separately from component severity.
Persistence, rendering and an already-blocked transport phase are outside a hard
wall-clock guarantee; this is not a fleet deadline or complete dependency check.

## Assessment vs health

| Concern | Question |
|---------|----------|
| Health | Is Admin API up? Is infra configured? |
| Assessment | HA, security, capacity, production config posture |

The broader component/dependency health contract (OIDC, timeouts, inaccessible dependencies and runtime evidence) is a [D2 deliverable](milestones/d2-rhbk-openshift.md). Current lightweight checks do not prove internal database health, failover, TLS certificate correctness or complete dependency coverage.
