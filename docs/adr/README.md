# Architecture Decision Records (ADRs)

Short records of **why** an accepted architectural decision was made.

## Format

Each ADR file:

```text
Status: Accepted | Proposed | Superseded
Context
Decision
Consequences
```

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-multi-target-by-design.md) | Multi-target by design | Accepted |
| [0002](0002-admin-rest-as-keycloak-integration-boundary.md) | Admin REST as Keycloak boundary | Accepted |
| [0003](0003-deterministic-assessment-engine.md) | Deterministic assessment engine | Accepted |
| [0004](0004-no-arbitrary-endpoints-from-mcp.md) | No arbitrary endpoints from MCP/REST | Accepted |
| [0005](0005-postgresql-is-not-a-tsdb.md) | PostgreSQL is not a TSDB | Accepted |
| [0006](0006-semantic-metrics-instead-of-raw-promql.md) | Semantic metrics instead of raw PromQL | Accepted |
| [0007](0007-plan-approve-apply-change-model.md) | Plan → Approve → Apply change model | Accepted |
| [0008](0008-ai-assists-backend-decides.md) | AI assists; backend decides | Accepted |
| [0009](0009-trust-boundaries-before-administration.md) | Trust boundaries before administration | Accepted |
| [0010](0010-explicit-installation-identity.md) | Explicit connection and installation identity | Accepted direction; partial implementation |
| [0011](0011-access-aware-dual-interface.md) | Access-aware assistant alongside the operator console | Accepted direction; implementation planned in AGT2 |
| [0012](0012-operator-managed-portable-platform.md) | Operator-managed installation, portable operations | Accepted direction; draft contract, implementation planned in OP1 |
| [0013](0013-registry-preflight-before-registration.md) | Administrative preflight before registry writes | Accepted for local preflight; registration remains planned |
| [0014](0014-registry-ownership-before-managed-writes.md) | Registry ownership before managed writes | Accepted configuration foundation; legacy adoption/API registration remain gated |

Requirements describe **what**; ADRs explain **why** a design satisfies them. Milestones track **when**.
