# Observability integration

## Metrics

- Platform exposes Micrometer/Prometheus at management `/q/metrics`.
- Target-facing metrics go through `MetricsService` → `MetricsProviderFactory` → provider.
- **No arbitrary PromQL** from API or MCP params.
- Categories: HTTP, DATABASE, JVM, CACHE, AUTHENTICATION, CLUSTER, RUNTIME.
- Windows: 1m–24h (`MetricWindow.parse`); interactive default 5m, assessment 15m.
- Missing values stay null / `NOT_AVAILABLE` — never coerced to 0.

See [metrics-catalog.md](metrics-catalog.md), [prometheus-integration.md](prometheus-integration.md),
[openshift-monitoring.md](openshift-monitoring.md).

PostgreSQL is not used as a metrics store.

## Tracing

Quarkus OpenTelemetry is enabled for the MCP/REST process. `TracingProvider` is a stub
for future external trace query backends.

## Logging

Structured audit via `AuditService` (+ optional DB). `LoggingProvider` stub for log backends.
