# Performance assessment

Evidence source `metrics` is optional for baseline profiles. Performance profiles
require it:

- `keycloak-production-performance`
- `rhbk-production-performance`
- `rhbk-openshift-production-performance`

`MetricsEvidenceCollector` builds a `PerformanceSummary` (assessment window, default 15m)
and emits boolean SLO evidence when `assessment.performance.*` thresholds are configured; deterministic rules turn that evidence into findings.

Metrics collection failures add `failedSources=metrics` and do **not** fail the whole
assessment unless the profile requires metrics.

An expired/interrupted summary preserves prior measurements but emits
`metrics.collection.complete=false`, becoming a partial source. This blocks a
COMPLETE assessment and available score when metrics are required; it does not
invalidate an otherwise complete static profile. Follow-on probes/inventory are
skipped after abort. Unknown histogram presence emits no missing-histogram finding.
The configurable summary/category deadline inherits the remaining common
assessment/report collection budget, including follow-on probes/inventory. This is
not a hard persistence/rendering or already-blocked I/O deadline;
see [observability architecture](architecture/observability.md).

## Measurements are not assessment policy

A live JVM/HTTP/database sample does not itself define an acceptable production threshold. For example, `metrics.jvm.heapUtilization` may be present while `metrics.jvm.heapPressure` remains missing because `assessment.performance.heap-utilization-warning-percent` is unset. FR-ASSESS-005 requires the corresponding rule to remain NOT_EVALUATED, not PASS. Percentile rules additionally require histogram samples; replica comparison needs infrastructure observations. Reports collect their performance summary independently from assessment evidence and may use a different query window.

The disposable identity lab explicitly sets a heap warning threshold of 85 percent to exercise the sample → threshold → deterministic assessment path. This is a test policy, not a production recommendation or a default added to the application. Other unset thresholds remain unset; neither AI nor the collector invents them. The [report-trust ledger](development/d1-report-trust-2026-09-11.md) distinguishes that local validation from full SLO/compatibility acceptance.

## Sustained database-awaiting evidence

`metrics.db.awaitingWarning` and `metrics.db.awaitingCritical` require
`DB_POOL_AWAITING_RANGE=AVAILABLE` plus finite nonnegative average and maximum,
with maximum not below average. Missing, malformed, stale or incomplete range
results emit neither boolean: dependent rules remain NOT_EVALUATED. A separate
instant measurement can still be displayed as current, but never substitutes for
window average/maximum. A validated zero range remains an observed negative
finding, not missing evidence. Range validity does not depend on a separate
instant query succeeding.

Returned evaluation-grid coverage is not continuous raw-scrape/instance coverage;
Prometheus lookback and aggregation remain separate limitations. See the
[inventory/temporal ledger](development/h1-evidence-temporal-2026-09-18.md).
