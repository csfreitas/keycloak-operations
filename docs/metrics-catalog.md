# Metrics catalog

Semantic metrics exposed by the platform. Callers never send PromQL.

## Matrix

Compatibility columns reflect **design expectations** for Keycloak/RHBK Micrometer exports.
Cells marked **NOT VERIFIED** mean no automated IT assertion in this repository yet.

| Semantic Metric | Underlying series (typical) | KC 26.6 | KC 26.7 | RHBK 26.6 | Histogram required | Optional feature | Stability |
|---|---|---|---|---|---|---|---|
| HTTP_REQUEST_RATE | `http_server_requests_seconds_count` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | HTTP metrics enabled | STABLE |
| HTTP_ERROR_RATE | `http_server_requests_seconds_count{outcome}` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | HTTP metrics enabled | STABLE |
| HTTP_AVERAGE_LATENCY | `http_server_requests_seconds_{sum,count}` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | HTTP metrics enabled | STABLE |
| HTTP_P50/P95/P99_LATENCY | `http_server_requests_seconds_bucket` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | **yes** | histograms enabled | STABLE |
| HTTP_ACTIVE_REQUESTS | `http_server_active_requests` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | HTTP metrics enabled | OPTIONAL |
| DB_POOL_* | `agroal_*_count` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | Agroal metrics | STABLE |
| JVM_HEAP_* | `jvm_memory_*_bytes{area="heap"}` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | JVM metrics | STABLE |
| JVM_GC_PAUSE | `jvm_gc_pause_seconds_max` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | JVM metrics | OPTIONAL |
| LOGIN_*/TOKEN_REFRESH/LOGOUT | `keycloak_user_events_total` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | event metrics (cardinality risk) | OPTIONAL |
| CACHE_HIT_RATIO | `cache_gets_total` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | cache metrics | OPTIONAL |
| KEYCLOAK_CLUSTER_SIZE | `vendor_cluster_size` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | Infinispan/vendor | OPTIONAL |
| RUNTIME_* | `container_*` | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED | no | cAdvisor / CRI-O | TROUBLESHOOTING |

## Windows & bounds

Windows: `1m`, `5m` (interactive default), `15m` (assessment default), `30m`, `1h`, `6h`, `24h`.

Configured limits (enforced): `metrics.max-range`, `metrics.max-series`, `metrics.max-points`, `metrics.stale-after`.

## Availability semantics

| Availability | Meaning |
|---|---|
| AVAILABLE | Fresh usable sample |
| NOT_AVAILABLE | Missing series / empty window / limit exceeded / error |
| STALE | Sample older than `metrics.stale-after` — not used for PASS findings |
| NOT_CONFIGURED | No metrics binding for the target |

Histogram **presence** is detected via bucket series count, not via whether p99 returned a value.
When buckets exist but the window has no traffic: `NO_TRAFFIC` (do not recommend enabling histograms).

Missing series stay `null` / `NOT_AVAILABLE` — never coerced to `0`.
