# Performance SLOs

Optional thresholds under `assessment.performance.*`:

| Property | Evidence / rule |
|---|---|
| `latency-p99-ms` | `metrics.slo.p99Exceeded` → KC-PERF-HTTP-001 |
| (same + missing buckets) | `metrics.http.histogram.requiredButMissing` → KC-PERF-HTTP-HIST-001 |
| `server-error-rate-percent` | `metrics.slo.errorRateExceeded` → KC-PERF-HTTP-002 |
| `db-awaiting-warning` | `metrics.db.awaitingWarning` → KC-PERF-DB-001 |
| `heap-utilization-warning-percent` | `metrics.jvm.heapPressure` → KC-PERF-JVM-001 |
| `minimum-cache-hit-ratio` | `metrics.cache.hitRatioBelowMinimum` → KC-PERF-CACHE-001 |

Leave properties unset in lab to disable SLO findings. Rules use `evidenceRequired`
so missing metrics yield `NOT_EVALUATED`, not false positives.
