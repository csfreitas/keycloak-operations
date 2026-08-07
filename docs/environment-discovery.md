# Environment Discovery

`EnvironmentDiscovery` classifies where the MCP server (or target Keycloak) appears
to run.

## Runtime types

| `RuntimeType` | Meaning |
|---------------|---------|
| `OPENSHIFT` | OpenShift APIs observed (`route.openshift.io` / `config.openshift.io`) |
| `KUBERNETES` | Kubernetes API observed without OpenShift API groups |
| `VM` | Reserved for future VM collectors |
| `UNKNOWN` | Not configured or not confirmed |

## Confidence

| `DetectionConfidence` | Meaning |
|-----------------------|---------|
| `CONFIRMED` | Real API evidence obtained |
| `DETECTED` | Partial signals |
| `UNKNOWN` | No confirmation |

The discovery service **never** claims `CONFIRMED` without API evidence.

## Configuration

```properties
discovery.kubernetes.enabled=false
discovery.openshift.enabled=false
```

Env overrides:

- `DISCOVERY_KUBERNETES_ENABLED`
- `DISCOVERY_OPENSHIFT_ENABLED`

When both are disabled (default for local unit tests / desktop),
`discover()` returns:

- `runtime = UNKNOWN`
- `confidence = UNKNOWN`
- evidence explaining that cluster probing is disabled

## MCP tool

`keycloak_discover_environment` exposes this read-only discovery for agents.

## Roadmap

Richer collectors (deployments, routes, PDB, resource requests) that feed the
Assessment Engine are planned for **0.2.0**.
