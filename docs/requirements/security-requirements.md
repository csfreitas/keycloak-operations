# Security requirements

Normative security controls. Related narrative: [architecture/security.md](../architecture/security.md).

## Target & endpoint binding

### SEC-TARGET-001

MCP/API callers **MUST NOT** provide arbitrary target endpoints (Keycloak, cluster, metrics, or other system URLs).

### SEC-TARGET-002

Operational access **MUST** resolve endpoints only from registered target configuration.

## Credentials & secrets

### SEC-CRED-001

Credentials **MUST** be resolved internally using credential references (`credentialRef` / credential providers).

### SEC-CRED-002

Secrets **MUST NOT** be returned through MCP or REST responses.

### SEC-CRED-003

Secrets **MUST NOT** be logged or written to audit payloads in plaintext.

### SEC-CRED-004

Secrets **MUST NOT** be stored as plaintext in PostgreSQL; persist references only.

### SEC-CRED-005

Outbound payloads **MUST** pass through sensitive-data filtering before return to clients.

## Metrics

### SEC-METRICS-001

MCP/REST callers **MUST NOT** submit arbitrary PromQL.

### SEC-METRICS-002

PromQL constructed by the backend **MUST** escape/validate semantic inputs so callers cannot alter query structure (injection resistance).

### SEC-METRICS-003

Metrics queries **MUST** apply mandatory target selectors / isolation constraints so one target cannot read another target’s series.

## Infrastructure

### SEC-INFRA-001

The platform **MUST NOT** expose arbitrary `kubectl` / `oc` / shell execution as tools or APIs.

### SEC-INFRA-002

The platform **MUST NOT** inventory or return Kubernetes Secret **contents** without an explicit, justified, documented need.

### SEC-INFRA-003

Infrastructure RBAC **SHOULD** follow least privilege (read-oriented assessor roles).

## Transport & defaults

### SEC-TLS-001

TLS certificate validation **MUST** be enabled by default (no trust-all / insecure skip by default).

### SEC-RO-001

Assessments and operational MCP tools **MUST** remain read-only by default (`mcp.read-only=true`). Controlled writes introduced in milestone 0.8 **MUST** still require explicit opt-out of global read-only mode plus target `WRITE`/`ADMIN` authorization and change-management policy/approval gates.

## Controlled writes

### SEC-CHANGE-001

MCP/REST **MUST NOT** expose raw Keycloak Admin write endpoints, arbitrary HTTP methods, or arbitrary JSON mutation tools.

### SEC-CHANGE-002

Change plans, diffs, audit events, logs, and API responses **MUST NOT** contain passwords, client secrets, IdP secrets, tokens, or private keys.

### SEC-CHANGE-003

Approval **MUST** be cryptographically or deterministically bound to the exact plan fingerprint so a modified plan cannot reuse a prior approval.

### SEC-CHANGE-004

Destructive operations **MUST** be denied by default in the 0.8 foundation.

### SEC-CHANGE-005

Change records and apply effects for Target A **MUST NOT** be readable or writable as Target B through authorization or query bugs.

### SEC-CLIENT-001

Redirect URI and Web Origin validation **MUST** reject malformed values and unsafe user-information or parent-directory forms. Full wildcards and other policy-defined unsafe patterns **MUST** be denied by default for production targets; any supported wildcard or non-HTTPS exception **MUST** be explicit, deterministic, environment-aware, and visible in the plan.

### SEC-CLIENT-002

Client update and create operations **MUST NOT** return, persist, log, fingerprint, or unintentionally overwrite client secrets, registration access tokens, private keys, or credential values. Apply **MUST** build the outbound representation from trusted read-back state plus allowlisted semantic operations.

### SEC-REALM-001

Realm changes that weaken transport, authentication, brute-force, event-audit, or account-security controls **MUST** be classified as high/critical risk or denied by deterministic policy. The LLM **MUST NOT** downgrade this outcome.

## Multi-target isolation

### SEC-MULTI-001

Data and credentials from one target **MUST NOT** leak into another target’s clients, queries, responses, or persisted records.

## Authorization

### SEC-AUTHZ-001

Target-scoped operations **SHOULD** enforce target authorization before execution.

### SEC-RBAC-001

Documentation and deploy manifests **SHOULD NOT** recommend `realm-admin` as the default Keycloak service-account role for this platform.

## Reports and AI

### SEC-REPORT-001

Operations reports **MUST** enforce target authorization and pass structured data and rendered output through sensitive-data filtering. Reports **MUST NOT** include credential values, tokens, passwords, private keys, Kubernetes Secret contents, or unrestricted environment variables.

### SEC-AI-001

Prompts, model context, model responses, and AI audit metadata **MUST NOT** become an alternate path around target isolation, semantic API validation, read-only mode, change policy, approval, or redaction.

### SEC-AI-002

Provider URLs, credentials, kubeconfig, bearer tokens, raw Admin REST paths, arbitrary PromQL, and arbitrary mutation payloads **MUST NOT** be accepted from an AI caller. The backend resolves configured providers from `targetId`.
