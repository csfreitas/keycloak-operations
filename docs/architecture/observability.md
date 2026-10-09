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

## Provider trust boundary

Prometheus-compatible requests use only registered endpoints, internally built
queries and internally resolved credentials. The HTTP client rejects endpoint
userinfo, query and fragment and never follows redirects. It does not forward
provider response/error text into public failures or logs. An explicitly configured
credential reference that throws, resolves to null or has no supported authentication
fails closed before transport; anonymous access is allowed only without a configured
reference. Target-selector isolation remains independent of the response checks below.

Before parsing, each response is capped at **1 MiB**, including chunked bodies.
A completion deadline also cancels a stalled body. Connection/request/body limits
are capped by an invocation-local monotonic budget (`metrics.operation-timeout-ms`,
default 30,000 ms, allowed 1–120,000 ms). Summary status, semantic queries, series
probes and the database range reuse that budget; category queries do likewise.
The body limit recomputes remaining time when headers arrive. No executor wrapper,
thread-local identity or asynchronous database transaction is introduced.

Expired/interrupted calls start no further provider requests or credential lookup,
discard late results and retain earlier valid values. The interrupt flag is preserved.
An aborted summary is DEGRADED with `availability.COLLECTION_BUDGET=NOT_AVAILABLE`;
its report performance section is PARTIAL. Metrics assessments receive a partial
collection marker; metrics partiality alone does not invalidate static profiles
that do not require metrics. This is a cooperative collection deadline, not a
hard real-time guarantee. It now also inherits the remaining target-bound common
collection budget when invoked by assessment/report/metrics application services.
Aborted collectors omit `metrics.source.available`: DEGRADED alone does not prove
that a connection or reachability check completed.

Presence probes return only observed keys; failed/stale/aborted results are unknown,
not observed absence, and incomplete sweeps are not cached. Cache hits require the
same target configuration. `HTTP_BUCKET_SERIES=UNKNOWN` prevents a failed probe
from creating a missing-histogram finding or proving NO_TRAFFIC.

The parser consumes response bytes directly, rejects malformed JSON, duplicate
fields and trailing documents, and caps nesting at 32, string values at 8,192
characters and number length at 128. A successful envelope must contain the
expected `status`, `data`, `resultType` and result-array structure. Instant calls
require vectors and range calls require matrices. Nonempty or malformed warnings,
mixed vector/matrix payloads and malformed sample structures are unavailable,
not successful empty observations. Labels are limited to 64 per series, with
128-character keys and 512-character textual values.

Malformed/nonfinite sample values are not emitted as usable numeric observations.
A mixed finite/nonfinite response is unavailable as a whole, not reduced to a
favorable finite subset. Timestamps are parsed at exact millisecond precision;
negative, overflowing or finer-than-supported timestamps are rejected instead of
rounded. Instant queries pin their evaluation time and reject future samples;
old evaluations remain STALE. Controlled semantic queries require exactly one
aggregate series, rather than selecting the first of multiple results.

Range requests use whole-second start/end/step, matching the values sent to the
API. Every returned series must contain exactly the inclusive evaluation grid
`start + n * step <= end`, with finite values and exact ordered timestamps.
Missing beginning/middle/end, duplicate, reversed, off-grid or out-of-window
points are unavailable. Existing series limits and the effective `maxPoints`
limit on actual samples remain in force. Semantic current/average/maximum come
only from one validated aggregate series; the mean avoids intermediate double
sum overflow. A failed/incomplete range does not use an instant value to invent
a window average, maximum or sustained database-awaiting finding.

NO_TRAFFIC requires an available zero HTTP request rate, histogram presence and
a known empty percentile response. Transport, parsing, timestamp and cardinality
failures are not relabeled idle traffic; NaN remains unavailable without enough
provenance to distinguish its cause.

## Scrape observations and ServiceMonitor association

`MetricsProvider.probeScrape` reads the individual target-scoped `up` series;
it does not count series to infer their values. Prometheus defines `up=1` as a
successful scrape and `up=0` as a failed scrape. A series can exist in either
case. See [Prometheus jobs and instances](https://prometheus.io/docs/concepts/jobs_instances/).

The controlled scrape query always requires `target_id` equal to the actual
registered target ID, in addition to the configured mandatory labels and namespace
scope. An alternative `target_id` tag or a shared `job` cannot substitute for it;
this stricter scrape contract does not rewrite legacy semantic-metric selectors.
Returned labels must match the query scope. The provider requires distinct label
sets, one finite binary sample per `up` series and a common, nonfuture evaluation
timestamp within the configured stale threshold. Invalid, empty, stale, excessive,
failed or interrupted observations supply no usable counts. `ScrapeObservation`
contains observed/successful/failed counts and evaluation time, not raw labels,
queries or provider diagnostics. Its request uses the existing metrics budget.

`ServiceMonitorProbe` combines two independent observations: a conservative
installation-to-monitor configuration association, and the registered target's
scrape observations. It requires an explicit Kubernetes/OpenShift installation
binding and an exactly matching configured/client namespace. `InventoryService`
revalidates target authorization and root identity, then uses bounded workload,
Pod/ReplicaSet and Service reads to establish exclusive Service association. It
retains the namespace Service snapshot internally so a monitor selector matching
both an associated Service and a foreign Service remains ambiguous. Raw Service
objects are not report/MCP payloads.

Only one monitor selecting one associated Service through one endpoint is
accepted. Monitor selectors match Service metadata labels, not Service Pod
selectors; equality and `In`, `NotIn`, `Exists`, `DoesNotExist` are supported with
validated label syntax. The probe implements a deliberately narrower subset than
the full Operator API: same-namespace selection only, a named TCP Service port
with a valid port number, and absent/empty target `relabelings`. Matching broad
namespace selectors, shared selections, multiple matches, `targetPort`-only
endpoints and unsupported/malformed configuration are UNKNOWN. Unrelated monitors
do not win by list order. Selector semantics follow the
[Kubernetes label contract](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/);
this is not a full ServiceMonitor schema validator.

The ServiceMonitor request asks for at most 101 entries and rejects more than 100,
pagination, nonzero remaining counts, duplicate identities and malformed resource
identity/envelopes. It uses the shared bounded cluster reader: at most 1 MiB and
five seconds per response (or a lower configured request timeout), with raw
envelope/metadata/items checks before model conversion. Omitted `items` is not an
empty list. Only absent item `apiVersion`/`kind` may inherit the validated outer
list's type; explicit null/conflicting types are rejected. Underlying inventory
lists have a 500-object ceiling. A known Service association plus a complete list with no
associated monitor yields SERVICEMONITOR_MISSING; missing/uncertain Service
association does not. Optional durations remain null when absent; present values
must be bounded positive ordered `y/w/d/h/m/s/ms` durations, and an explicit
scrape timeout cannot exceed an explicit interval. Effective inherited defaults
are not inferred. The [Operator endpoint API](https://prometheus-operator.dev/docs/api-reference/api/#monitoring.coreos.com/v1.Endpoint)
defines named Service ports and the interval/timeout relationship.

After successful association, all observed matching scrapes succeeding yields
SCRAPE_HEALTHY; any observed failure yields SCRAPE_TARGET_DOWN. Missing, stale or
failed metrics yield UNKNOWN while retaining independently known monitor presence.
Unsupported metrics providers yield METRICS_DISABLED. ServiceMonitor access denial
is PERMISSION_DENIED with no fabricated configuration; failures expose fixed
messages, not metadata or exception text. Existing explicit cluster permissions
are required for these reads; the change adds no RBAC grants, cluster mutations,
ambient kubeconfig fallback or Secret-content access.

These results do not establish that Prometheus/its Operator selected or reconciled
the monitor, that an observed series came from this exact monitor/endpoint, that
every expected instance was scraped, or that raw samples are continuously fresh.
Evaluation timestamps are not raw scrape timestamps. Service/monitor reads are
independent rather than an atomic cluster snapshot. The subsequent shared reader
replaces generated list defaults for the integrated cluster collectors and adds
per-response byte/parser/time bounds; it is not a full Kubernetes/Operator schema
validator. Factory-created cluster clients disable redirects and request retries
without changing configured TLS/authentication or permissions. The whole
association and follow-on inventory now inherit the common target collection
deadline; the existing metrics-local budget cannot extend it. The [scrape-readiness ledger](../development/h1-scrape-readiness-2026-09-18.md)
preserves that slice's execution evidence; the
[inventory-envelope ledger](../development/h1-inventory-envelopes-2026-09-18.md)
tracks the subsequent reader implementation and validation status. No live
Operator/OpenShift compatibility acceptance is implied.

## Remaining evidence limits

Provider reachability, sample availability, component health, assessment posture
and collection completeness are distinct. Complete returned evaluation points do
not prove continuous raw scrapes or complete constituent-instance coverage:
Prometheus lookback and aggregation may reuse old samples or hide missing inputs.
Evaluation timestamps are not raw scrape timestamps. A newly started local TSDB
may legitimately lack most of a requested window; its range remains unavailable.
The report's common collection budget propagates to Admin reads, Fabric8, metrics
and ServiceMonitor follow-on work. Persistence/rendering and already-blocked
transport phases remain outside a hard wall-clock guarantee. Later work does not
start after expiry, and valid partial measurements are preserved. Generic external
`MetricsProvider` implementations receive cooperative
pre/post guards but must implement transport deadlines themselves. Byte/cardinality
limits also do not establish that every returned label is trustworthy or free of
sensitive content; output filtering and configured source scope remain required.

See the [local failure/bounds evidence](../development/h1-failure-bounds-2026-09-18.md)
and [dependency review](../development/h1-dependency-review-2026-09-18.md). H1 remains
open; subsequent [inventory/temporal evidence](../development/h1-evidence-temporal-2026-09-18.md)
records the stricter response contract and warning/evidence mapping. No real
RHBK/OpenShift compatibility claim follows from synthetic response tests.

The [operation-budget ledger](../development/h1-operation-budgets-2026-09-18.md)
records the subsequent bounded compound-metrics and credential-failure slice.

## Detailed docs

| Doc | Topic |
|-----|--------|
| [../observability-integration.md](../observability-integration.md) | Integration overview |
| [../prometheus-integration.md](../prometheus-integration.md) | Prometheus provider |
| [../openshift-monitoring.md](../openshift-monitoring.md) | OpenShift Monitoring |
| [../metrics-catalog.md](../metrics-catalog.md) | Semantic metric catalog |
| [../performance-assessment.md](../performance-assessment.md) | Performance assessment |
| [../performance-slo.md](../performance-slo.md) | SLO / policy notes |
