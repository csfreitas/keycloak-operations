# Evidence catalog

Stable evidence keys emitted by infrastructure / Keycloak collectors.
Every Evidence includes `targetId`.

| Key | Category | Type | Notes |
|-----|----------|------|-------|
| `runtime.type` | runtime | string | `OPENSHIFT` / `KUBERNETES` / `UNKNOWN` |
| `cluster.distribution` | cluster | string | `openshift` / `kubernetes` |
| `cluster.version` | cluster | string | K8s or OCP version |
| `cluster.platform` | cluster | string | From OpenShift Infrastructure status when available |
| `cluster.nodes.count` | cluster | int | |
| `cluster.zones.count` | cluster | int | Distinct `topology.kubernetes.io/zone` |
| `keycloak.deployment.method` | workload | string | `KEYCLOAK_OPERATOR` / `DEPLOYMENT` / `STATEFULSET` / `UNKNOWN` |
| `keycloak.replicas.desired` | workload | int | |
| `keycloak.replicas.ready` | workload | int | |
| `deployment.replicas` | workload | int | **Compat** alias of desired replicas (HA rules) |
| `keycloak.replicas.readyBelowDesired` | workload | bool | ready &lt; desired when both known |
| `keycloak.pods.total` | pods | int | |
| `keycloak.pods.ready` | pods | int | |
| `keycloak.pods.restartCount` | pods | int | Sum across pods |
| `keycloak.pods.oomKilledCount` | pods | int | |
| `keycloak.topology.zoneCount` | topology | int | |
| `keycloak.topology.podsByZone` | topology | map | zone → count |
| `keycloak.topology.podsByNode` | topology | map | node → count |
| `keycloak.topology.singleZoneConcentration` | topology | bool | multi-zone cluster, all pods in one zone |
| `keycloak.topology.singleNodeConcentration` | topology | bool | all pods on one node |
| `keycloak.scheduling.zoneSpread.present` | scheduling | bool | |
| `keycloak.scheduling.hostnameSpread.present` | scheduling | bool | |
| `keycloak.hpa.present` | autoscaling | bool | |
| `keycloak.hpa.minReplicas` | autoscaling | int | |
| `keycloak.hpa.maxReplicas` | autoscaling | int | |
| `keycloak.pdb.present` | disruption | bool | |
| `keycloak.resources.requests.cpu` | resources | string | |
| `keycloak.resources.requests.memory` | resources | string | |
| `keycloak.resources.limits.cpu` | resources | string | |
| `keycloak.resources.limits.memory` | resources | string | |
| `keycloak.resources.requests.cpu.present` | resources | bool | Presence flag (preferred by capacity rules) |
| `keycloak.resources.requests.memory.present` | resources | bool | |
| `keycloak.resources.limits.memory.present` | resources | bool | |
| `metrics.source.available` | performance | bool | Metrics backend reachable |
| `metrics.window` / `metrics.source` | performance | string | Provenance |
| `metrics.http.*` / `metrics.db.*` / `metrics.jvm.*` | performance | number | Nullable — omitted when missing |
| `metrics.http.histogram.available` | performance | bool | |
| `metrics.slo.p99Configured` / `p99Exceeded` / `errorRateExceeded` | performance | bool | When SLO configured |
| `metrics.http.histogram.requiredButMissing` | performance | bool | p99 SLO without buckets |
| `metrics.db.awaitingWarning` / `metrics.jvm.heapPressure` | performance | bool | Threshold findings |
| `keycloak.probes.readiness.present` | probes | bool | From pod template |
| `keycloak.probes.liveness.present` | probes | bool | |
| `keycloak.probes.startup.present` | probes | bool | |
| `keycloak.route.present` | networking | bool | Route or Ingress |
| `collection.warning.<resource>` | collection | string | Warning code when a section failed |
| `keycloak.version` | server | string | From Keycloak Admin API collector |
| `keycloak.product` | server | string | `KEYCLOAK` / `RHBK` |
| `keycloak.realm.count` | realm | int | |

Secret values are never emitted.
