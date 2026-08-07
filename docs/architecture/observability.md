# Observability architecture

Semantic metrics for registered Keycloak/RHBK targets. Callers never submit raw PromQL.

```text
Keycloak /metrics → Prometheus / Thanos / OpenShift Monitoring
        → MetricsProvider (target-bound)
        → MetricsService
        → REST / MCP / MetricsEvidenceCollector → Assessment
```

## Principles

- Target-aware endpoint + credential resolution ([ADR 0006](../adr/0006-semantic-metrics-instead-of-raw-promql.md))
- PostgreSQL is not the metrics store ([ADR 0005](../adr/0005-postgresql-is-not-a-tsdb.md))
- Missing metrics → graceful degradation for static assessments ([NFR-RES-001](../requirements/non-functional-requirements.md))

## Detailed docs

| Doc | Topic |
|-----|--------|
| [../observability-integration.md](../observability-integration.md) | Integration overview |
| [../prometheus-integration.md](../prometheus-integration.md) | Prometheus provider |
| [../openshift-monitoring.md](../openshift-monitoring.md) | OpenShift Monitoring |
| [../metrics-catalog.md](../metrics-catalog.md) | Semantic metric catalog |
| [../performance-assessment.md](../performance-assessment.md) | Performance assessment |
| [../performance-slo.md](../performance-slo.md) | SLO / policy notes |
