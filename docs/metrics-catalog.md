# Metrics catalog

Semantic metrics exposed by the platform. Callers never send PromQL.

| Semantic metric | Category | Stability | Histogram | Unit |
|---|---|---|---|---|
| HTTP_REQUEST_RATE | HTTP | STABLE | no | rps |
| HTTP_ERROR_RATE | HTTP | STABLE | no | percent |
| HTTP_AVERAGE_LATENCY | HTTP | STABLE | no | ms |
| HTTP_P50/P95/P99_LATENCY | HTTP | STABLE | yes | ms |
| HTTP_ACTIVE_REQUESTS | HTTP | OPTIONAL | no | count |
| DB_POOL_* | DATABASE | STABLE | no | count/ratio |
| JVM_HEAP_* / JVM_GC_PAUSE | JVM | STABLE/OPTIONAL | no | bytes/ratio/ms |
| LOGIN_*/TOKEN_REFRESH/LOGOUT | AUTHENTICATION | OPTIONAL | no | rps |
| CACHE_HIT_RATIO | CACHE | OPTIONAL | no | ratio |
| KEYCLOAK_CLUSTER_SIZE | CLUSTER | OPTIONAL | no | count |
| RUNTIME_* | RUNTIME | TROUBLESHOOTING | no | cores/bytes |

Windows: `1m`, `5m` (interactive default), `15m` (assessment default), `30m`, `1h`, `6h`, `24h`.

Missing series stay `null` / `NOT_AVAILABLE` — never coerced to `0`.
