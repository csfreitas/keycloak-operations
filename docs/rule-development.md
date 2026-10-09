# Rule Development

## When to write a Java rule vs YAML

| Approach | Use when |
|----------|----------|
| Java (`Rule` implementation) | Complex logic, multi-key correlation, custom messaging |
| YAML under `src/main/resources/rules/` | Simple threshold / equality conditions |

## Java rule checklist

1. Implement `io.github.keycloakmcp.assessment.engine.Rule`
2. Stable id (for example `KC-OCP-HA-001`)
3. `applies()` checks required evidence keys exist
4. `evaluate()` returns `Optional.empty()` when healthy (preferred), or a `Finding`
5. Severity from `Severity` enum
6. Include impact, recommendation, and references
7. Add a unit test through `RuleEngine`

Reference implementation: `MinimumReplicasRule`.

## YAML shape (0.5)

Packs are listed in `rules/index.yaml` (JAR-safe). Supported condition operators:
`equals`, `notEquals`, `lessThan`, `lessThanOrEqual`, `greaterThan`,
`greaterThanOrEqual`, `exists`, `notExists`, `empty`, `notEmpty`, `contains`,
`notContains`, `sizeGreaterThan`, `sizeLessThan`, plus `all` / `any` composites.

```yaml
rules:
  - id: KC-OCP-HA-001
    title: Minimum Keycloak replicas for HA
    category: high-availability
    severity: HIGH
    description: ...
    impact: ...
    recommendation: ...
    references: []
    condition:
      key: deployment.replicas
      lessThan: 2
    appliesWhen:
      runtime: [OPENSHIFT, KUBERNETES]
```

See [rule-catalog.md](rule-catalog.md). Java `MinimumReplicasRule` is kept for unit
tests; production HA uses the YAML pack.

## Directory layout

```
src/main/resources/rules/
├── index.yaml
├── common/           # security.yaml, production.yaml, admin-security.yaml
├── openshift/        # ha.yaml
├── capacity.yaml
└── performance.yaml
```

This is the indexed production pack layout. Version-specific and host/container packs remain planned; an unindexed legacy file does not automatically become an active rule pack.

## Evidence keys

Rules must document the evidence keys they consume. Collectors are responsible for
producing those keys. Example:

| Key | Type | Producer (planned / current) |
|-----|------|------------------------------|
| `deployment.replicas` | number | Current inventory evidence; desired replicas, not healthy replicas |
| `realm.bruteForceProtected` | boolean | Implemented Keycloak collector; realm-scoped subject |

## Testing

Prefer the Evidence → Rule → Finding pipeline test style used in `RuleEngineTest`:

1. Build `Evidence` with the key under test
2. Wrap in `EvidenceContext`
3. Run `RuleEngine.evaluate`
4. Assert finding id / severity, or emptiness when healthy

Also test absent/denied/truncated evidence and reversed collection order across two realms. Missing evidence is NOT_EVALUATED, never a passing empty result. Production packs must declare required evidence/applicability, stable entity scope and references. See [scoring](scoring.md) for score availability and [D2](milestones/d2-rhbk-openshift.md) for curated live rule acceptance.
