# Performance assessment

Evidence source `metrics` is optional for baseline profiles. Performance profiles
require it:

- `keycloak-production-performance`
- `rhbk-production-performance`
- `rhbk-openshift-production-performance`

`MetricsEvidenceCollector` builds a `PerformanceSummary` (assessment window, default 15m)
and emits boolean SLO findings when `assessment.performance.*` thresholds are configured.

Metrics collection failures add `failedSources=metrics` and do **not** fail the whole
assessment unless the profile requires metrics.
