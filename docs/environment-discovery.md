# Environment Discovery

`EnvironmentDiscovery.discover(Target)` classifies where a **registered Target**
runs, using the Target's infrastructure binding and `InfrastructureClientFactory`.

## Runtime types

| `RuntimeType` | Meaning |
|---------------|---------|
| `OPENSHIFT` | OpenShift APIs observed (`route.openshift.io` / `config.openshift.io`) |
| `KUBERNETES` | Complete API-group response observed without the OpenShift route/config groups |
| `VM` | Reserved |
| `UNKNOWN` | Not configured or not confirmed |

Classification uses one complete `/apis` observation per discovery call — never
hostname heuristics or the configured platform hint. Failed, denied, malformed or
over-limit discovery stays UNKNOWN; an unavailable API-group list is not evidence
of Kubernetes without OpenShift. The separate `/version` read runs at most once;
its failure leaves the version unknown without discarding independently observed
runtime classification.

## Confidence

| `DetectionConfidence` | Meaning |
|-----------------------|---------|
| `CONFIRMED` | Complete, validated API-group evidence obtained |
| `DETECTED` | Reserved; the current cluster classifier does not emit this value |
| `UNKNOWN` | No confirmation |

## Configuration

Explicit target connection (required for cluster discovery; placeholders are administrator-supplied):

```properties
mcp.targets.customer-a-prd.infrastructure.type=OPENSHIFT
mcp.targets.customer-a-prd.infrastructure.cluster-id=approved-cluster-a
mcp.targets.customer-a-prd.infrastructure.namespace=rhbk
mcp.targets.customer-a-prd.infrastructure.credential-ref=ocp-a
```

Legacy global flags remain configuration-compatible but do not enable ambient cluster probing:

```properties
discovery.kubernetes.enabled=false
discovery.openshift.enabled=false
```

The no-argument global discovery entrypoint returns UNKNOWN. Missing/unsupported infrastructure remains target-scoped UNKNOWN without probing another cluster. Choose exactly one explicit credential mode in [infrastructure authentication](infrastructure-authentication.md).

Connection ID, namespace and credential reference must be explicit. The resolved
cluster client's scope must match the target namespace; its default namespace is
never a fallback. Interrupted calls do not start new reads. Discovery failures use
fixed diagnostics, not raw provider errors or exception causes.

Environment discovery identifies the connected platform; it does not identify a Keycloak installation or prove that a workload serves the target URL. Inventory requires exact API/kind/name/UID binding. Existing persisted targets offer a separate [candidate confirmation flow](development/installation-onboarding-2026-09-11.md); new target/connection registration and host/container collectors remain planned.

## Observation limits

Each cluster response is limited to 1 MiB and a deadline of at most five seconds
(or the lower configured request timeout), with bounded JSON parsing. API discovery
requires the actual `APIGroupList` envelope and explicit groups array: at most 100
groups and 32 served versions per group, with consistent unique identities. Valid
non-Kubernetes-style CRD version names are supported. A configured OpenShift type
does not repair missing evidence.

These per-request controls now also inherit a target-bound discovery/report
collection budget. It is cooperative, not a hard wall-clock response guarantee,
full Kubernetes schema validator or real-cluster compatibility acceptance. The
[inventory-envelope ledger](development/h1-inventory-envelopes-2026-09-18.md)
tracks this implementation and its validation status.

## MCP / REST

- MCP: `keycloak_discover_environment` (requires `targetId`)
- REST: `GET /api/v1/targets/{targetId}/environment`

See also [infrastructure-inventory.md](infrastructure-inventory.md) and
[evidence-catalog.md](evidence-catalog.md).
