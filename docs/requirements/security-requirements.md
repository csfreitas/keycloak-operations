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

Assessments and operational MCP tools **MUST** be read-only by default unless a later milestone explicitly introduces controlled writes.

## Multi-target isolation

### SEC-MULTI-001

Data and credentials from one target **MUST NOT** leak into another target’s clients, queries, responses, or persisted records.

## Authorization

### SEC-AUTHZ-001

Target-scoped operations **SHOULD** enforce target authorization before execution.

### SEC-RBAC-001

Documentation and deploy manifests **SHOULD NOT** recommend `realm-admin` as the default Keycloak service-account role for this platform.
