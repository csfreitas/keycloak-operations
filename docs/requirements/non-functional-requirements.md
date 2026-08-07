# Non-functional requirements

## Isolation & correctness

### NFR-ISO-001

Multi-target isolation **MUST** be preserved for Keycloak admin clients, infrastructure clients, metrics queries, and persistence.

### NFR-DET-001

Assessments **MUST** be deterministic for the same evidence and rule configuration.

### NFR-CHANGE-001

Change risk classification, policy evaluation, approval validity, stale-plan detection, and verification outcomes **MUST** be deterministic for the same inputs. The LLM **MUST NOT** own those decisions.

## Resilience & degradation

### NFR-RES-001

Failure or absence of Prometheus/metrics **MUST NOT** prevent static (non-metrics) assessment rules from running.

### NFR-RES-002

When metrics are unavailable, related performance rules **MUST** degrade gracefully (for example `NOT_EVALUATED` / partial completeness) rather than inventing zero values or false PASS.

### NFR-RES-003

Target and provider calls **SHOULD** use timeouts and fail closed with structured errors.

## Bounds & performance

### NFR-BOUND-001

Metrics and inventory collection **MUST** apply bounds (range, series, samples, response size) to avoid unbounded queries.

### NFR-BOUND-002

The platform **SHOULD** avoid uncontrolled N+1 remote calls when collecting evidence for a single assessment run.

### NFR-TSDB-001

PostgreSQL **MUST NOT** be used as a time-series database for continuous metric samples; Prometheus/Thanos (or equivalent) **MUST** remain the metrics source of truth.

## Observability of the platform itself

### NFR-OBS-001

The platform **SHOULD** expose its own operational metrics/health suitable for deployment monitoring (distinct from Keycloak target metrics).

## Testability & compatibility

### NFR-TEST-001

Core security and isolation behaviors **MUST** be covered by automated tests.

### NFR-TEST-002

Integration tests that require private registries or live clusters **MAY** be opt-in; documentation **MUST NOT** claim they ran when they were skipped.

### NFR-COMPAT-001

Public APIs and tool contracts **SHOULD** remain backward compatible when reasonable across minor SNAPSHOT iterations within a milestone series.
