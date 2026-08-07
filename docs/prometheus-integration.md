# Prometheus integration

Lab compose includes Prometheus on port `9090` scraping Keycloak management `/metrics`.

Configure per target:

```properties
mcp.targets.lab-keycloak-a.observability.metrics.type=PROMETHEUS
mcp.targets.lab-keycloak-a.observability.metrics.endpoint=http://localhost:9090
```

Optional platform fallback: `platform.metrics.prometheus.endpoint`.

Queries are built internally via `MetricsQueryBuilder` with mandatory target selectors.
REST/MCP never accept raw PromQL.

Smoke: `scripts/smoke-metrics.sh` (connectivity only — not a load test).
