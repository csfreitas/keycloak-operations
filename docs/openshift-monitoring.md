# OpenShift Monitoring integration

Set target metrics type to `OPENSHIFT_MONITORING`. Namespace scope is preferred;
cluster scope is explicit via `observability.metrics.scope=CLUSTER`.

Endpoint defaults to in-cluster Thanos Querier when unset
(`platform.metrics.openshift.endpoint` can override).

Credentials use `credential-ref` → `CredentialProvider.getMetricsCredentials`
(bearer / basic). Tokens are never logged or returned to MCP clients.

## Scrape identity is explicit

The scrape-readiness probe queries individual `up` observations with
`target_id="<registered target ID>"`, alongside configured mandatory labels and
namespace scope. The query does not accept another `target_id` from target tags,
and cluster scope does not remove this identity requirement. This is independent
of the existing semantic-metric selector behavior. If the monitoring pipeline
does not expose the actual target ID on its `up` series, readiness stays UNKNOWN;
the platform does not fall back to namespace-wide/shared-job health or install
labels or relabeling rules automatically.

`up=0` is a failed scrape even when the series exists. All validated observed
values of 1 mean SCRAPE_HEALTHY for those observations; any 0 means
SCRAPE_TARGET_DOWN. Missing/stale/malformed results are UNKNOWN, not healthy or
an invented zero. These observations do not enumerate expected instances or
prove continuous raw-scrape freshness. The underlying metric is documented in
[Prometheus jobs and instances](https://prometheus.io/docs/concepts/jobs_instances/).

## Supported ServiceMonitor association

The target must have an explicit approved installation binding and namespace.
The probe revalidates the root identity and uses the shared bounded Service
association collector, rather than choosing the first namespace monitor. It
requires exactly one matching monitor, one exclusively associated Service and
one endpoint referencing a named TCP Service port. Selectors use Service metadata
labels; the Service's Pod selector serves the separate installation association.

The supported subset accepts `matchLabels` and the four set-selector operators
`In`, `NotIn`, `Exists`, `DoesNotExist`. Namespace selection must resolve only to
the current namespace. A matching selector that also selects a foreign Service,
cross-namespace/`any` selection, multiple candidate associations, nonempty target
`relabelings`, malformed fields or a `targetPort`-only endpoint remains UNKNOWN.
This conservative subset is not a rejection or full validation of all valid
[Prometheus Operator configurations](https://prometheus-operator.dev/docs/api-reference/api/#monitoring.coreos.com/v1.ServiceMonitorSpec).

The monitor list is capped at 100 results, with a 101-entry request used to detect
overflow. Pagination, duplicate identities and invalid envelopes are rejected;
the dedicated list model does not turn omitted `items` into observed absence.
No associated Services is UNKNOWN. A complete monitor list without a match for
known associated Services is SERVICEMONITOR_MISSING within the configured
namespace, not proof that no monitor exists elsewhere.

Optional interval/timeout values are returned only after bounded positive duration
validation. When both are explicit, timeout must not exceed interval. Absent
values remain null; global Prometheus defaults are not guessed. The
[Operator endpoint contract](https://prometheus-operator.dev/docs/api-reference/api/#monitoring.coreos.com/v1.Endpoint)
describes these fields. Association does not prove Operator selection,
reconciliation, effective scrape configuration or an exact monitor-to-series join.

## Access and remaining limits

Cluster and metrics credentials remain separate. Association needs the existing
authorized namespace reads for the installation/workloads, Pods, ReplicaSets,
Services and ServiceMonitors. The implementation adds no privileges or RBAC
changes; unavailable scope yields UNKNOWN rather than searching other namespaces.
A ServiceMonitor list 401/403 is PERMISSION_DENIED with null configuration fields.
No Secret contents are requested, and raw resource/exception details are not
returned. See [explicit infrastructure connections](architecture/portable-environment-discovery.md).

The shared cluster reader validates raw typed envelopes and byte/parser/count/time
bounds before model defaults. Association inherits the common target collection
budget, including metrics follow-on work; its reads are not an atomic snapshot or
a hard response-time SLA. This local
implementation is not real OpenShift/Operator compatibility certification;
execution and pending acceptance are recorded in the
[scrape-readiness ledger](development/h1-scrape-readiness-2026-09-18.md).
