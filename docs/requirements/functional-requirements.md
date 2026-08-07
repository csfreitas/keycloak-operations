# Functional requirements

Stable IDs for product capabilities present or planned through the documented roadmap (through **0.8**). Language: **MUST** / **SHOULD** / **MAY**.

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

### FR-UI-003

When Change Management is enabled, the Web UI **SHOULD** provide a minimal change experience (pending changes, detail with diff/risk/approval/apply/verification, and history) without becoming a full Keycloak administration console.

## Controlled administration & change management (milestone 0.8)

### FR-CHANGE-001

Mutable Keycloak/RHBK operations **MUST** use semantic, target-bound change requests. Callers **MUST NOT** supply arbitrary Admin REST paths, HTTP methods, or unconstrained JSON mutation payloads.

### FR-CHANGE-002

Before any Keycloak mutation, the platform **MUST** produce a ChangePlan from current target state, including a safe normalized diff and a deterministic plan fingerprint.

### FR-CHANGE-003

Diff representations **MUST** use normalized change kinds (`ADDED`, `REMOVED`, `CHANGED`, `UNCHANGED`) and **MUST NOT** expose secret values (use redacted / configured markers instead).

### FR-CHANGE-004

Risk classification (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) **MUST** be deterministic backend logic. The LLM **MUST NOT** decide risk.

### FR-CHANGE-005

Environment-aware policy evaluation **MUST** run before apply. Production (`PRD`) writes **MUST** require explicit approval by default. Policy decisions **MUST NOT** be delegated to the LLM.

### FR-CHANGE-006

Destructive operations (including generic delete) **MUST** remain denied / out of scope for the 0.8 foundation unless a later milestone explicitly enables them with stronger controls.

### FR-CHANGE-007

Target authorization **MUST** distinguish at least `READ`, `ASSESS`, `PLAN`, `WRITE`, and `ADMIN`. Authorization to mutate Target A **MUST NOT** grant access to Target B.

### FR-CHANGE-008

When policy requires approval, apply **MUST** refuse unapproved plans. Approval **MUST** be bound to the exact plan fingerprint; modifying the plan **MUST** invalidate prior approval.

### FR-CHANGE-009

Apply **MUST** accept a previously planned change identifier (not arbitrary desired state) when approval is required, and **MUST** re-check the target baseline before mutation. Material drift **MUST** fail with a replan/conflict error rather than silently overwriting.

### FR-CHANGE-010

After a successful Admin API mutation, the platform **MUST** read the resource back, compare to desired state, and persist a verification result. HTTP success alone **MUST NOT** be treated as verified desired state.

### FR-CHANGE-011

Change lifecycle actions (plan, approve, reject, apply, verify) **MUST** produce sanitized audit records that never contain secret values.

### FR-CHANGE-012

Apply **SHOULD** be idempotent where technically possible so repeated execution of the same approved plan does not create unintended duplicate resources or repeated side effects.

### FR-CHANGE-013

The platform **MUST** expose semantic Change Management MCP tools for get/list/approve/reject/apply/verify, plus semantic planning for the initial proof-of-concept mutation. Raw Admin write tools **MUST NOT** exist.

### FR-CHANGE-014

Equivalent Change Management REST endpoints under `/api/v1` **MUST** share the same application services as MCP.

### FR-CHANGE-015

Change lifecycle state **MUST** be persisted in PostgreSQL via versioned Flyway migrations without storing plaintext secrets.
