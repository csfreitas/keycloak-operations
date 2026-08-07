# Observability integration

## Metrics

- Platform exposes Micrometer/Prometheus at management `/q/metrics`.
- Target-facing metrics go through `MetricsService` → `MetricsProvider`.
- **No arbitrary PromQL** from API or MCP params. Only semantic categories:
  `requests`, `latency`, `jvm`, `database-pool`, `resources`.
- `PrometheusMetricsProvider` maps semantics to fixed internal queries.
  If `platform.metrics.prometheus.endpoint` is unset, results are unsupported/empty.

PostgreSQL is not used as a metrics store.

## Tracing

Quarkus OpenTelemetry is enabled for the MCP/REST process. `TracingProvider` is a stub
for future external trace query backends.

## Logging

Structured audit via `AuditService` (+ optional DB). `LoggingProvider` stub for log backends.
