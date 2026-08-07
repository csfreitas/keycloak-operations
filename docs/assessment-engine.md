# Assessment Engine

The Assessment Engine turns **Evidence** into **Findings** and a numeric **score**.
It is designed to be platform-agnostic: rules never depend on Fabric8 or Keycloak
representation types.

## Pipeline

```
Collectors → Evidence → EvidenceContext → Rules → Findings → AssessmentScoring → AssessmentResult
```

### Evidence

```java
record Evidence(String source, String category, String key, Object value, Instant collectedAt)
```

Example key: `deployment.replicas` with numeric value `1`.

### Rules

Rules implement:

- `applies(EvidenceContext)` — whether the rule is relevant
- `evaluate(EvidenceContext)` — optional `Finding` when a problem is detected

Java rule example: `MinimumReplicasRule` (`KC-OCP-HA-001`).

YAML packs under `src/main/resources/rules/` provide declarative stubs/loaders
(`YamlRuleLoader`) for the same conditions.

### Findings

Findings include id, title, category, severity (`CRITICAL`…`INFO`), status
(`OPEN`, `PASS`, `WARNING`, `FAIL`), description, evidence map, impact,
recommendation, and references.

When replicas ≥ 2, `MinimumReplicasRule` emits **no** finding (preferred over a
PASS finding in 0.1.0).

### Scoring

`AssessmentScoring` starts at **100** and subtracts:

| Severity | Penalty |
|----------|---------|
| CRITICAL | 25 |
| HIGH | 15 |
| MEDIUM | 8 |
| LOW | 3 |
| INFO | 0 |

`PASS` findings do not reduce the score. Floor is **0**.

## Profiles (built-in names)

| Profile | Intent |
|---------|--------|
| `keycloak-production` | Community Keycloak production baseline |
| `rhbk-production` | RHBK production baseline |
| `rhbk-openshift-production` | RHBK on OpenShift |
| `keycloak-kubernetes-production` | Keycloak on Kubernetes |

Full profile runners and MCP assessment tools are planned for **0.2.0+**.
0.1.0 ships the engine abstractions, one concrete HA rule, YAML stubs, and unit tests.

## Assessment vs health check

| Concern | Question |
|---------|----------|
| Health | Is the process / endpoint up? |
| Assessment | Is the deployment production-ready (HA, security, capacity, architecture)? |

Do not conflate `/q/health` with assessment scores.
