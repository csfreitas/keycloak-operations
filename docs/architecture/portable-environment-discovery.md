# Portable environment discovery and inventory

Status: accepted product direction, staged implementation. Explicit cluster connections, exact installation binding, scoped networking and existing-target candidate confirmation are implemented locally (V9–V10). New connection/target registration, host/container collectors and normalized inventory history remain **not implemented**. Local validation does not establish real cluster compatibility; see the delivery table below.

## Domain boundaries

The planned [installation Operator](operator-managed-platform.md) manages the
Operations hub, not these sources. Direct collection remains the first path;
optional collectors/offline import retain independent approval and acceptance.
No connection, credential or target permission follows from installing the product.

The central `targetId` identifies a logical Keycloak/RHBK environment, not a Kubernetes namespace. Keycloak Admin REST, explicit management endpoints and configured metrics can support assessments independently of hosting. Configured product/runtime are operator declarations, never observed facts or proof of vendor support.

Keep three independent dimensions on an installation in the future normalized model:

- Hosting: VM, physical host, Kubernetes/OpenShift cluster, managed/unknown.
- Runtime: JVM process, Docker, Podman, Kubernetes-managed container, other/unknown.
- Deployment management: Operator, Deployment/StatefulSet, Compose, systemd/Quadlet, manual standalone, other/unknown.

A VM running Podman Compose is one installation with these dimensions, not three mutually exclusive environments. Standalone describes a deployment method, not proof of one replica, no clustering or absent HA. Compose is grouping/configuration, not a separate container engine. OS/architecture, versions and provenance are observations where available. Do not extend the legacy `InfrastructureType` enum with every combination and accidentally route Docker/Podman through a Kubernetes client.

## Connections, environments, installations, observations

1. **Connection**: administrator-approved source endpoint/host identity, authentication reference, TLS/host-key policy and permitted scope. Keycloak, cluster, container/host collector and metrics connections remain separate. Connection management/discovery permissions are not implied by target READ.
2. **Environment**: logical target, owner/team, classification, authorization and assessment profile. A realm/client is normally an inventoried child, not an automatic target.
3. **Installation binding**: explicit approved resource identity within a connection. Initially one installation per target; future multi-installation environments must retain installation-scoped evidence and cannot infer HA from installation count.
4. **Discovery candidate/run**: scoped findings, matching rationale, ambiguity and access gaps. Discovery does not register environments or grant access automatically. Confirmation creates a binding, not workloads or infrastructure.
5. **Inventory snapshot**: immutable bounded observations with source, target/installation identity, collection window, collector/schema version, warnings and freshness. History/diff are separate from configuration. Retention and deletion policy are required before continuous collection.

The [exact installation slice](../development/exact-installation-binding-2026-09-11.md) adds an optional binding to the existing target configuration, V9 persistence and read-only target API. Old targets remain unbound without guesses. The subsequent [confirmation slice](../development/installation-onboarding-2026-09-11.md) introduces expiring retained candidates, managed-binding revision, transactional confirmation audit and a UI for existing persisted targets with approved cluster connections. It does not introduce new connection/target registration. The configured cluster ID is an administrator connection identifier, not an observed cluster UID.

The [2026-09-18 local identity validation](../development/d1-installation-identity-2026-09-18.md) exercises discovery and confirmation through real local OIDC identity against an explicitly configured synthetic Kubernetes API. A separate setup principal has READ/DISCOVER/BIND; ordinary readers retain READ/ASSESS. Two loopback application instances share the disposable database: only the dedicated setup instance disables global read-only, while the second proves otherwise-authorized binding is denied with read-only enabled. Confirmation persists the selected binding, consumed run and mandatory audit; the cluster receives only scoped reads. This fixture validates the existing product path without changing backend/UI production code, default grants, migrations or report schema.

The synthetic API serves two distinct deployments in each of `lab-a` and `lab-b`, allowing ambiguity and exact UID selection to be tested. A separate fixture-control credential can replace a UID or deny reads; it is not the application's cluster credential. Request evidence is bounded and excludes authorization values. Expired-run rejection uses explicit fault injection in the runner-owned transient database, not a claim of waiting through the production expiry interval. Repeated automated runs and the browser setup flow are recorded in the dated ledger: candidates start unselected, confirmation requires selection and acknowledgement, the UID change is reviewed, and the saved binding is audited. After logout, an ordinary reader sees the persisted binding with setup controls disabled. The other target remains unchanged and the synthetic cluster receives no writes. Real Kubernetes/OpenShift compatibility, operator-resource behavior and broader H1/D1 acceptance remain open.

## Discovery routes and scope

- Known Keycloak URL: an authorized administrator registers the connection and target; use Admin REST/health/metrics without requiring host or cluster access.
- Cluster: discover candidates only within explicit namespace scope. Prefer Operator resources; otherwise identify Deployment/StatefulSet candidates. Bind using connection identity + namespace + kind + name + UID; follow ownership/selectors. Multiple matches require confirmation, never first-match selection. Discover served API versions rather than assuming one CRD version. Resource names reused after recreation require reconciliation, not silent rebinding.
- Docker/Podman: use an approved host-local collector or a separately governed runtime connection. Scope to approved container IDs/labels/project. Preserve engine/host identity and rootless user context; matching container names across hosts are not identity.
- Compose: resolve project and service identities plus observed engine resources. Record provider/version and label availability; do not assume Docker and every Podman Compose provider expose identical metadata. A compose file describes intent, not observed readiness, current membership or successful deployment.
- VM/physical/standalone: use an explicitly installed bounded collector/exporter or operator-generated evidence bundle. Bind host identity plus service/unit or process identity including start time; a PID alone is reusable. Do not scan the user's SSH config, filesystem or all processes by default. Windows/non-systemd hosts require their own capability/compatibility tests.
- Other platforms: retain Admin REST support when available and report unsupported infrastructure explicitly. A new collector must satisfy the same authorization, schema, redaction, timeout and test contract before being advertised.

Prefer an authenticated local collector that exposes only allowlisted observations; an offline sanitized bundle is a useful alternative for restricted customer environments. Neither transport exists yet. Remote shell, arbitrary commands, container exec and model-generated scripts are not part of discovery. Installing a collector, enabling a socket, opening a firewall or collecting host data requires explicit operator approval.

## Capability-based collection and assessment

Planned collectors declare supported capabilities, e.g. IAM_CONFIGURATION, SERVICE_HEALTH, JVM_METRICS, HOST_RESOURCES, CONTAINER_RUNTIME, SERVICE_MANAGER, WORKLOAD_TOPOLOGY and NETWORK_EXPOSURE. Track **collector support**, **connection configuration**, **authorization** and **observation outcome** separately.

Outcomes must distinguish observed, not configured, unsupported, permission denied, unreachable, stale and ambiguous. An empty result proves absence only for a successful, complete query of the relevant scope. Missing evidence is not zero. Desired replicas are not observed healthy replicas. A declared VM or Docker label does not establish runtime identity.

Use common facts for common rules (authentication posture, latency, resource pressure, process availability) and platform-specific packs for platform semantics. PodDisruptionBudget rules are not applicable to a confirmed standalone JVM; host/systemd rules need actual host evidence. If the platform itself is unknown, mark applicability/evaluation unknown rather than automatically not applicable. Do not translate every host process into a synthetic Kubernetes pod.

Snapshots retain resource relationships and configured-versus-observed values; reports show infrastructure, IAM and metrics coverage separately. No overall infrastructure completeness percentage when the expected resource set is unknown. Imported bundles are explicitly offline and preserve collection time; a hash alone proves neither trusted origin nor authenticity.

### Implemented cluster observation boundary

Discovery now retains operator intent (`configuredType`) separately from observed
runtime and fixed Route/config v1 API capabilities. A complete bounded `/apis`
response yields `SERVED`, `NOT_SERVED` (group absent) or `UNSUPPORTED_VERSION`
(group advertised without the collector's v1); failed/unperformed observation is
`UNKNOWN`. These states describe advertisement only, not authorization, successful
resource reads, freshness beyond this collection or full cluster visibility.

Inventory and networking consume that same discovery result. They do not consult
the legacy wrapper's configured type/OpenShift handle as capability evidence and
do not run implicit detection. Fixed Route/config reads use the approved base
client only when v1 was advertised. Unknown/unsupported Route coverage remains
partial while valid Services/Ingresses survive; version failure preserves runtime
and API observations but prevents complete source coverage. Configured VM is
unsupported, distinct from absent infrastructure configuration. No VM/container
collector is introduced. Early binding/budget failures may omit detailed discovery;
they remain explicitly incomplete.

The inventory carries discovery into new snapshots. One shared completeness policy
is used by assessment evidence, snapshot/report status and infrastructure API health;
legacy missing discovery/coverage cannot establish completeness. This is a first
cluster-specific reconciliation slice, not the planned normalized all-source
capability/authorization/freshness model. See the
[capability evidence ledger](../development/h1-capability-coverage-2026-09-19.md).

The Kubernetes/OpenShift paths now validate raw responses before Fabric8 model
conversion. The shared reader admits only fixed resource operations, actual
matching envelopes and explicit list metadata/items; missing `items` cannot become
a generated empty-list default. Typed-list entries may inherit only absent
`apiVersion`/`kind` from the validated outer type. Conflicting/null entry types are
rejected, and individual GETs require their own envelope and requested identity.
Namespaced identities must match the explicit target/cluster scope. Pagination,
duplicates, malformed or over-limit responses remain incomplete, not absence.

Each response has a 1 MiB cap and a deadline of at most five seconds (or a lower
configured request timeout), with bounded parsing. Inventory lists cap at 500
objects; ServiceMonitor lists and combined installation candidates cap at 100.
API discovery caps at 100 groups and 32 served versions per group. Factory-created
clients preserve configured TLS/authentication but disable redirects and request
retries. Previously validated independent inventory sections remain usable;
candidate discovery does not return a successful partial set.

Environment classification reads `/apis` once and never confirms a runtime from a
failed response or configured type alone. A failed `/version` observation does not
erase independently observed platform identity. These reads now inherit the common
target-bound collection deadline, without creating a hard response-time SLA,
complete Kubernetes schema validation, an atomic
snapshot or real-cluster acceptance. See the
[inventory-envelope ledger](../development/h1-inventory-envelopes-2026-09-18.md)
for implementation and validation status; portable host/container collectors
remain planned.

## Security and local hygiene

- No ambient kubeconfig, current Docker context, default Podman socket, host SSH config or local filesystem fallback for target operations. Existing cluster-client paths now require explicit credentials and scope; mounted service-account access is opt-in without external kubeconfig fallback, and legacy global probing is disabled. See [migration and validation](../development/explicit-infrastructure-connections-2026-09-11.md). Docker/Podman/host connectors remain unimplemented.
- A Docker/Podman socket is a privileged capability, not a read-only credential. Mounting its path read-only does not make its API read-only. Keep it out of the MCP/UI process; govern and minimize any host collector with socket access. Rootless limits the host identity, not the API's write capabilities.
- Export an allowlist of fields. Exclude unrestricted environment variables, command lines, inspect dumps, compose interpolation, Secret contents, private keys and mounted file contents. Sanitize before storage or transmission. Treat labels and names as untrusted data, never instructions.
- Query only registered sources; candidate URLs cannot trigger automatic outbound requests. Set duration, size, resource-count and concurrency limits. No arbitrary HTTP, PromQL, SSH or command passthrough.
- Inventory may identify exited containers or unused resources but must not remove them. Cleanup is a separately authorized workflow. Tests use purpose/run labels and transient storage; preserve reusable volumes/images and never globally prune.
- Both disposable identity runners share an atomic ownership lock before runtime checks or startup. Existing/stale locks and unowned resources are preserved; cleanup releases only a verified owned lock. JWT log verification fails when scanning cannot complete, rather than reporting a clean log from a scanner error. These are local-runner safeguards, not production coordination or a complete secret-detection guarantee.

Primary references: [Docker daemon security](https://docs.docker.com/engine/security/), [Podman API security](https://docs.podman.io/en/stable/markdown/podman-system-service.1.html), [Compose service/project labels](https://docs.docker.com/reference/compose-file/services/), [Kubernetes ownership](https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/).

## Delivery and acceptance

| Slice | Scope | Acceptance / actual support |
|---|---|---|
| Portable foundation | Requirements/model; prevent unrelated cluster fallback | Tests: no binding, NONE, VM and missing credential reference return target-scoped UNKNOWN without cluster access; supported explicit binding still uses the target client |
| Full onboarding — partially delivered | Connections, candidate confirmation, installation binding, capability status | Existing-target binding/confirmation delivered below; new connection/target registration remains open. Acceptance requires distinct installations never silently merge, no auto-grants and migration/API/UI tests |
| Exact cluster binding — backend slice | Explicit root API/kind/name/UID in configured connection/namespace, V9 persistence, ownership-scoped workload/pods | Implemented locally; see validation ledger and subsequent confirmation slice |
| Installation networking — backend slice | Exclusive Service selector/ownership checks; Ingress backend and OpenShift Route association | [Locally validated configuration association](../development/installation-networking-2026-09-11.md); ambiguous or unavailable collections are partial. No live networking, certificate or cluster compatibility claims |
| Candidate confirmation — existing cluster targets | Expiring scoped candidates, explicit selection, READ/DISCOVER/BIND, read-only gate, UID/revision/context checks, V10 and mandatory audit | [Implementation validation](../development/installation-onboarding-2026-09-11.md) and [real local identity with synthetic cluster API](../development/d1-installation-identity-2026-09-18.md); no default grants or cluster writes. Full connection/target registration, broader browser acceptance and real-cluster compatibility remain open |
| Local container collector | Podman + Compose first, then Docker | Real scoped fixtures for running/exited, ambiguous names, resource recreation, rootless identity, denial, redaction, timeouts; distinct Docker validation required |
| Host/standalone collector | VM/physical, JVM/service manager; sanitized offline evidence | Real Linux VM/systemd and standalone fixtures; no required SSH/root; PID reuse, inaccessible files, secrets, stale bundles, limited privileges; other OS explicitly unverified |
| Inventory/history | Normalized snapshots, differences, coverage and rule applicability | Unreachable/denied is not removed; desired is not ready; identical IAM findings across hosting types for identical evidence; target isolation and freshness gates |

These slices complement, not replace, the real RHBK/OpenShift presentation gate. No new delivery dates or claims of live Docker/VM support are implied. Current available infrastructure collector remains Kubernetes/OpenShift; local container-based Keycloak tests prove API integration, not a Docker/Podman infrastructure collector.

Implementation backlog: [ONB1 registration/reconciliation](../milestones/onb1-environment-registry.md), [PORT1 containers](../milestones/port1-container-inventory.md), [PORT2 host/offline](../milestones/port2-host-offline-inventory.md), [D3E retained evidence](../milestones/d3e-evidence-replay.md) and [P1 semantic drift](../milestones/p1-iam-continuous-observability.md). Accepted identity rationale: [ADR 0010](../adr/0010-explicit-installation-identity.md).

ONB1 now has an initial [administrative preflight](../registry-preflight.md): opt-in
REST validates a bounded draft and local ID/reference metadata only. It neither
registers a connection nor performs discovery, destination approval, credential
validation or binding. This partial slice does not implement the remaining portable
registration/collector work in the delivery table.

The subsequent [configuration ownership foundation](registry-ownership.md) adds V11,
explicit provenance, an ORM registry revision and transactional system audit. This
guards existing configuration reconciliation without registering new connections via
API, adopting legacy rows or changing the discovery/binding permission model.
