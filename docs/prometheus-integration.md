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

## Scrape readiness versus series presence

Scrape readiness uses a controlled direct `up` query, not `count(up)`. Prometheus
records 1 for a successful scrape and 0 for a failed scrape, so a present series
alone cannot prove health. See [jobs and instances](https://prometheus.io/docs/concepts/jobs_instances/).
The probe validates each returned binary observation and exposes bounded aggregate
counts, not raw label sets. Any observed failure means SCRAPE_TARGET_DOWN; all
observed successes mean SCRAPE_HEALTHY. Empty, stale, malformed or failed responses
remain UNKNOWN, and counts are not an expected-instance coverage denominator.

This scrape query always pins `target_id` to the actual registered target ID,
including when tags configure another value or a shared job. Other configured
mandatory selectors and namespace scope still apply and are validated on returned
series. This stricter rule is specific to scrape observations; existing
semantic-metric selectors are unchanged. The source must already provide the
matching label on `up`; the platform neither rewrites Prometheus configuration
nor falls back to broader series if it is absent.

On explicitly bound Kubernetes/OpenShift targets, ServiceMonitor readiness also
requires the conservative Service/monitor association described in the
[OpenShift Monitoring guide](openshift-monitoring.md). A plain Prometheus
deployment does not acquire a ServiceMonitor or cluster binding automatically.
Monitor association and the matching `up` observations are independent evidence:
neither proves Operator selection, exact monitor provenance, all expected targets,
or continuous raw-sample freshness. See the
[scrape-readiness ledger](development/h1-scrape-readiness-2026-09-18.md) for this
increment's implementation and execution status.

## Response handling

Configured endpoints must be HTTP(S) without embedded user credentials, query or
fragment. Authentication is resolved separately; redirects are rejected rather
than followed. Do not configure a redirecting login page as the metrics API.
If a configured credential reference cannot supply valid bearer/basic authentication,
collection fails without trying anonymous access. Intentionally anonymous endpoints
must omit the credential reference.

Responses are limited to **1 MiB before JSON parsing**, including chunked bodies,
with a received-body completion deadline. Existing connection/request timeouts
also apply, capped by the remaining shared metrics-operation budget. Configure
`metrics.operation-timeout-ms` (default `30000`, valid `1`–`120000`) for a summary
or category collection. These calls also inherit the outer
`collection.operation-timeout-ms` budget (same default/range) when invoked by
assessment/report services. Neither is a hard persistence/rendering deadline.
Slow headers consume the same budget as the body. Expiry/interruption stops further
queries, preserves earlier observations and marks collection partial. Missing
presence-probe keys mean unknown and incomplete sweeps are not cached.

The client rejects duplicate fields, trailing JSON documents, malformed
envelopes, missing status/data, nonempty or malformed backend warnings, and a
vector/matrix type that does not match the requested API. Parsing limits are
32 nesting levels, 8,192 characters per string value and 128 characters per
number. Each series allows at most 64 textual labels, with keys up to 128 and
values up to 512 characters. Rejection is reported as unavailable/degraded,
without returning raw backend payloads or error messages.

Malformed/nonfinite numeric values are not usable samples. Mixed responses are
unavailable as a whole, not reduced to a favorable finite subset. Controlled
semantic queries require exactly one aggregate series. The configured series
limit is checked on returned data. Range
results also enforce `max(2, metrics.max-points)` against actual samples
per series, not merely against the requested step. A range failure leaves the
range summary unavailable: an instant value is not substituted for the requested
window's average or maximum.

Timestamps must be exact, finite and within the requested evaluation grid; missing,
duplicate, reversed, future or off-grid range points are unavailable. This validates
returned evaluation points, not raw-scrape continuity or all source instances.
See [observability architecture](architecture/observability.md), the
[temporal ledger](development/h1-evidence-temporal-2026-09-18.md) and the
[operation-budget ledger](development/h1-operation-budgets-2026-09-18.md) for
implemented boundaries and remaining acceptance work.
