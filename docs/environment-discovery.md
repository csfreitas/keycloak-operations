# Environment Discovery

`EnvironmentDiscovery.discover(Target)` classifies where a **registered Target**
runs, using the Target's infrastructure binding and `InfrastructureClientFactory`.

## Runtime types

| `RuntimeType` | Meaning |
|---------------|---------|
| `OPENSHIFT` | OpenShift APIs observed (`route.openshift.io` / `config.openshift.io`) |
| `KUBERNETES` | Kubernetes API observed without OpenShift API groups |
| `VM` | Reserved |
| `UNKNOWN` | Not configured or not confirmed |

Classification uses API groups — never hostname heuristics.

## Confidence

| `DetectionConfidence` | Meaning |
|-----------------------|---------|
| `CONFIRMED` | Real API evidence obtained |
| `DETECTED` | Partial signals |
| `UNKNOWN` | No confirmation |

## Configuration

Target binding (preferred):

```properties
mcp.targets.customer-a-prd.infrastructure.type=OPENSHIFT
mcp.targets.customer-a-prd.infrastructure.namespace=rhbk
mcp.targets.customer-a-prd.infrastructure.credential-ref=ocp-a
```

Global flags (fallback / in-cluster lab only):

```properties
discovery.kubernetes.enabled=false
discovery.openshift.enabled=false
```

## MCP / REST

- MCP: `keycloak_discover_environment` (requires `targetId`)
- REST: `GET /api/v1/targets/{targetId}/environment`

See also [infrastructure-inventory.md](infrastructure-inventory.md) and
[evidence-catalog.md](evidence-catalog.md).
