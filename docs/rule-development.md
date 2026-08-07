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
├── common/           # security.yaml, production.yaml
├── openshift/        # ha.yaml
├── kubernetes/       # planned
├── keycloak/26.6/    # version-specific packs (planned)
├── keycloak/26.7/
├── rhbk/26.6/
└── vm/               # planned
```

## Evidence keys

Rules must document the evidence keys they consume. Collectors are responsible for
producing those keys. Example:

| Key | Type | Producer (planned / current) |
|-----|------|------------------------------|
| `deployment.replicas` | number | OpenShift/K8s collector (0.2.0); tests inject directly |
| `realm.bruteForceProtected` | boolean | Keycloak collector (planned) |

## Testing

Prefer the Evidence → Rule → Finding pipeline test style used in `RuleEngineTest`:

1. Build `Evidence` with the key under test
2. Wrap in `EvidenceContext`
3. Run `RuleEngine.evaluate`
4. Assert finding id / severity, or emptiness when healthy
