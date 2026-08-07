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

Requirements describe **what**; ADRs explain **why** a design satisfies them. Milestones track **when**.
