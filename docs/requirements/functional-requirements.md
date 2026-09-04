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

## Realm and client administration (milestone 0.8.1)

### FR-CLIENT-001

Client administration **MUST** use typed semantic requests for each supported operation. Collection-valued settings such as redirect URIs and Web Origins **MUST NOT** be accepted through the milestone 0.8 generic scalar `desiredState` contract or through arbitrary `ClientRepresentation` payloads.

### FR-CLIENT-002

The platform **MUST** support controlled replacement of a client's complete `redirectUris` and `webOrigins` sets. Inputs **MUST** be bounded, validated, normalized, de-duplicated, and represented deterministically before diff, fingerprint, persistence, apply, and verification.

### FR-CLIENT-003

Collection changes **MUST** produce deterministic, reviewable evidence of values added and removed. Reordering or duplicate removal alone **MUST NOT** create an effective change.

### FR-CLIENT-004

The platform **MUST** deterministically classify and evaluate policy for redirect URI and Web Origin changes based on the effective transition, including scheme, wildcard use, environment, additions, and removals. The caller or LLM **MUST NOT** provide the resulting risk or policy decision.

### FR-CLIENT-005

The platform **MUST** support a typed client security and flow request for PKCE, Authorization Code flow, Implicit flow, Direct Access Grants, service accounts, and public/confidential client semantics. Omitted fields **MUST** remain unchanged; arbitrary attributes and complete `ClientRepresentation` payloads **MUST NOT** be accepted.

### FR-CLIENT-006

Client security and flow changes **MUST** validate effective combinations before planning. PKCE **MUST** accept only the supported `S256` and explicit `NONE` modes. Service accounts **MUST NOT** be enabled for an effective public client.

### FR-CLIENT-007

Risk and policy for client security and flow settings **MUST** be transition-aware. Enabling Implicit flow, Direct Access Grants, or service accounts; weakening PKCE; and changing public/confidential client-authentication semantics **MUST NOT** be classified as generic Boolean changes. Unsafe weakening transitions **MUST** be denied by default in production.

### FR-CLIENT-008

Client creation **MUST** use a typed allowlist, reject duplicate `clientId` values, default to a disabled public OpenID Connect client, and use the controlled plan/approve/apply/verify lifecycle. Requests, persistence, responses, fingerprints, and audit data **MUST NOT** accept or expose client secrets.
Creating a client with Direct Access Grants enabled **MUST** be denied by default in production.

### FR-CLIENT-009

Enabling or disabling an existing client **MUST** be a typed transition with deterministic risk, approval policy, stale-baseline protection, secret-cleared update, and read-back verification. Enabling **MUST** be classified at least HIGH and disabling at least MEDIUM.

### FR-REALM-001

Realm administration **MUST** expose only explicitly supported, non-sensitive, typed properties. Realm create/update/enable/disable operations **MUST** use the controlled change lifecycle and **MUST NOT** accept arbitrary realm attributes or a complete caller-supplied `RealmRepresentation`.

### FR-REALM-002

Every supported realm property **MUST** define deterministic validation, normalized diff, risk, environment policy, stale-plan detection, read-back verification, and compatibility behavior before it is exposed through MCP or REST.

### FR-CHANGE-016

MCP and REST planning surfaces for 0.8.1 **MUST** call the same application service and use equivalent typed contracts. Apply, approval, rejection, verification, persistence, and audit **MUST** continue to use the milestone 0.8 shared lifecycle.

## Operations reporting and AI assistance

### FR-REPORT-001

The platform **MUST** generate a target-bound, point-in-time operations report through a shared application service used by REST and MCP.

### FR-REPORT-002

The report **MUST** distinguish report-generation completeness from target health and assessment status. Missing evidence **MUST NOT** be represented as a healthy state, a zero metric, or a deterministic PASS.

### FR-REPORT-003

The report **MUST** combine the sanitized evidence available for the target, including product and environment metadata, infrastructure inventory, health checks, deterministic assessment results, actionable findings, and semantic performance metrics when configured.

### FR-REPORT-004

Each report section **MUST** declare `COMPLETE`, `PARTIAL`, `FAILED`, or `SKIPPED`. Failure of one provider **MUST NOT** discard independently collected useful sections.

### FR-REPORT-005

The platform **MUST** expose both structured report data and a deterministic human-readable representation suitable for operators and AI agents. It **MUST NOT** require an LLM to calculate the report.

### FR-REPORT-006

Platform-specific report evidence **MUST** identify the collector and actual coverage. OpenShift/Kubernetes evidence may be included when configured; VM, Docker, or other runtime evidence **MUST** be marked unavailable until a real collector exists.

### FR-AI-001

AI agents **MAY** discover, orchestrate, correlate, summarize, and explain operational capabilities through MCP, but **MUST NOT** determine health, PASS/FAIL, severity, score, evidence completeness, risk, policy, authorization, approval validity, conflict, or verification outcome.

### FR-AI-002

AI-facing tools **SHOULD** return compact, typed, provenance-bearing responses that reference persisted assessments, health checks, snapshots, changes, and reports instead of returning unbounded raw provider objects.

### FR-AI-003

Any future model-generated explanation **MUST** be visibly separate from deterministic facts, include references to the source report or evidence identifiers, and remain optional to core platform operation.
