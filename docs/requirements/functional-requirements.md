# Functional requirements

Stable IDs for product capabilities present or planned through the documented roadmap (through **0.7**). Language: **MUST** / **SHOULD** / **MAY**.

## Targets

### FR-TARGET-001

Every operational call (MCP or REST) **MUST** identify a registered target using `targetId`.

### FR-TARGET-002

The platform **MUST** support multiple Keycloak/RHBK targets in one deployment.

### FR-TARGET-003

The platform **MUST** expose target list/get/find capabilities for operators and agents.

### FR-TARGET-004

Callers **MUST NOT** supply arbitrary Keycloak, cluster, or metrics endpoint URLs; endpoints **MUST** come from registered target configuration.

## Keycloak administration (read)

### FR-ADMIN-001

The platform **MUST** provide read-only access to Keycloak/RHBK Admin data for realms, clients, users, groups, roles, and server info via structured tools/APIs.

### FR-ADMIN-002

Admin integration **MUST** prefer the public Admin REST API over Keycloak internal server APIs when both can satisfy the use case.

### FR-ADMIN-003

Outbound admin representations **MUST** be sanitized (no client secrets or credential material in DTOs).

## Discovery & infrastructure

### FR-DISC-001

The platform **MUST** support environment discovery for a registered target (OpenShift, Kubernetes, VM/unknown as applicable).

### FR-INV-001

When infrastructure is configured for a target, the platform **MUST** collect a structured infrastructure inventory (workload, pods, topology, and related objects as permitted by RBAC).

### FR-INV-002

Inventory and discovery **MUST** be target-aware (per-target clients and namespaces).

## Assessment

### FR-ASSESS-001

The platform **MUST** collect normalized evidence before executing assessment rules.

### FR-ASSESS-002

Assessment results **MUST** expose findings, overall score, evidence completeness, and confidence when depth features are enabled.

### FR-ASSESS-003

Assessment evaluation **MUST** be deterministic for a given evidence set and rule pack (the LLM **MUST NOT** decide PASS/FAIL).

### FR-ASSESS-004

The platform **SHOULD** support assessment profiles that select rule packs appropriate to the environment.

### FR-ASSESS-005

Rules **MAY** return `NOT_EVALUATED` when required evidence or thresholds are absent; that outcome **MUST NOT** be treated as PASS.

## Health

### FR-HEALTH-001

The platform **MUST** execute operational health checks independently from architecture/production-readiness assessments.

### FR-HEALTH-002

Health check history **SHOULD** be persistable for later query via REST/MCP.

## Metrics & performance

### FR-METRICS-001

The platform **MUST** expose semantic runtime metrics without exposing raw PromQL to MCP/REST callers.

### FR-METRICS-002

Metrics queries **MUST** be bound to a registered target’s observability configuration.

### FR-METRICS-003

The platform **SHOULD** support Prometheus, Thanos (via Prometheus-compatible query API), and OpenShift Monitoring providers, plus a no-op/disabled mode.

### FR-METRICS-004

HTTP latency percentiles (p50/p95/p99) **MUST** be computed only when histogram buckets are available; otherwise availability **MUST** be reported as not available (never approximated from averages).

### FR-METRICS-005

The platform **SHOULD** expose performance summaries and feed metrics into assessment evidence when configured.

### FR-METRICS-007

The platform **MUST** enforce configured metrics query bounds (max range, max series, max points). Requests that exceed max range **MUST** fail with a controlled error (not silent truncation).

### FR-METRICS-008

When HTTP histogram bucket series exist but the selected window has no observations, percentile metrics **MUST** report no-traffic (not “histogram disabled”).

### FR-METRICS-009

Metric samples older than the configured stale threshold **MUST** be marked stale and **MUST NOT** drive performance PASS findings as if current.

## Persistence & platform API

### FR-PERS-001

The platform **MUST** persist operational history (targets, assessments, health, audit, snapshots as applicable) in PostgreSQL via versioned migrations.

### FR-REST-001

The platform **MUST** expose a versioned REST API (`/api/v1`) sharing application services with MCP.

### FR-MCP-001

MCP tools and REST controllers **MUST** share application services (no duplicated business logic).

### FR-AUDIT-001

Tool and API invocations **SHOULD** be auditable with sanitized payloads.

## Web UI (milestone 0.7)

### FR-UI-001

The Web UI **MUST** consume only backend REST/SSE; the browser **MUST NOT** call Keycloak Admin, OpenShift/Kubernetes, Prometheus, or PostgreSQL directly.

### FR-UI-002

The Web UI **SHOULD** provide fleet, target overview, health, assessment, findings, performance, infrastructure, and history views.
