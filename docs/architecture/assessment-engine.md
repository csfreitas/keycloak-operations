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
(`OPEN`, `PASS`, `WARNING`, `FAIL`, `NOT_EVALUATED`, `SKIPPED`), description,
evidence map, impact, recommendation, references, and optional `EvidenceSubject`.

Declarative YAML packs under `src/main/resources/rules/` (indexed by
`rules/index.yaml`) are the primary rule source. Java `MinimumReplicasRule`
remains for unit tests; production HA uses `KC-OCP-HA-001` from the `ha` pack.

### Scoring

See [scoring.md](../scoring.md). Category scores and NOT_EVALUATED handling land in 0.5.

## Profiles (built-in names)

See [assessment-profiles.md](../assessment-profiles.md) and [rule-catalog.md](../rule-catalog.md).

## Assessment vs health check

See [health-check.md](../health-check.md).
