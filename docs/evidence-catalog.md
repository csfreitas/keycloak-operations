# Evidence catalog

Stable evidence keys emitted by infrastructure / Keycloak collectors.
Every Evidence includes `targetId`.

Evidence may also carry an entity `subject`; realm rules evaluate the matching realm, not the first value of a duplicated key. Null/unavailable observations and collection issues must remain explicit. This catalog lists implemented keys, not a claim every source supplies them in every environment.

| Key | Category | Type | Notes |
|-----|----------|------|-------|
| `runtime.type` | runtime | string | `OPENSHIFT` / `KUBERNETES` / `UNKNOWN` |
| `cluster.distribution` | cluster | string | `openshift` / `kubernetes` |
| `cluster.version` | cluster | string | K8s or OCP version |
| `cluster.platform` | cluster | string | From OpenShift Infrastructure status when available |
| `cluster.nodes.count` | cluster | int | |
| `cluster.zones.count` | cluster | int | Distinct `topology.kubernetes.io/zone` |
| `keycloak.deployment.method` | workload | string | `KEYCLOAK_OPERATOR` / `DEPLOYMENT` / `STATEFULSET` / `UNKNOWN` |
| `keycloak.workload.uid` / `keycloak.workload.kind` / `keycloak.workload.apiVersion` | workload | string | Exact observed workload identity; binding provenance remains separate |
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
| `metrics.source.available` | performance | bool | Legacy provider-status availability; omitted on budget abort because DEGRADED alone does not prove reachability |
| `metrics.collection.complete` | performance | bool | False after summary budget expiry/interruption; partial source, not a whole-collector coverage guarantee |
| `metrics.window` / `metrics.source` | performance | string | Provenance |
| `metrics.http.*` / `metrics.db.*` / `metrics.jvm.*` | performance | number | Nullable — omitted when missing |
| `metrics.http.histogram.available` | performance | bool | Emitted only when presence/absence was observed; unknown is omitted |
| `metrics.slo.p99Configured` / `p99Exceeded` / `errorRateExceeded` | performance | bool | When SLO configured |
| `metrics.http.histogram.requiredButMissing` | performance | bool | Configured p99 SLO with observed bucket presence/absence; failed probe is not absence |
| `metrics.db.awaitingWarning` / `metrics.jvm.heapPressure` | performance | bool | Threshold findings |
| `keycloak.probes.readiness.present` | probes | bool | From pod template |
| `keycloak.probes.liveness.present` | probes | bool | |
| `keycloak.probes.startup.present` | probes | bool | |
| `keycloak.route.present` | networking | bool | Route or Ingress |
| `collection.warning.<resource>` | collection | string | Warning code when a section failed |
| `keycloak.version` | server | string | From Keycloak Admin API collector |
| `keycloak.version.raw` | server | string | Full version only when actually observed in Admin API metadata |
| `keycloak.product` | server | string | Observed `KEYCLOAK` / `RHBK`, nullable when unknown |
| `keycloak.product.configured` | server | string | Operator declaration; never substituted for observed product |
| `keycloak.realm.count` | realm | int | |

Secret values are never emitted.
