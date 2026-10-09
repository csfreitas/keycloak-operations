# Infrastructure authentication

## Modes

| Mode | When | Config |
|------|------|--------|
| `IN_CLUSTER` | Explicit `mcp.credentials.<ref>.in-cluster=true` | Mounted ServiceAccount token/CA; no ambient fallback |
| `TOKEN` | `mcp.credentials.<ref>.token` set | `token` + `api-server-url` + optional `ca-cert-data` |
| `KUBECONFIG` | Explicit `mcp.credentials.<ref>.kubeconfig` path | Bounded static file; exact current context, inline credentials/CA |

Configure exactly one mode per credential reference and an explicit target namespace. Missing references or ambiguous modes do not select in-cluster authentication. Static kubeconfig rejects exec/auth-provider plugins, external certificate/key/CA paths and basic credentials; it does not inspect the user's default kubeconfig. Non-loopback API endpoints require HTTPS, without URL user-info, query or fragment.

## TLS

- Default: TLS verification **enabled** (`trust-insecure=false`)
- `trust-insecure=true` only for local/dev (e.g. CRC with self-signed API)
- Never the production default

## Isolation

- Clients are cached per `targetId` + fingerprint of resolved configuration, credential reference, infrastructure type and cluster identifier; credential/CA changes replace the client
- Target A credentials cannot be reused for Target B through the cache
- LLM never supplies API URL, token, kubeconfig, or CA

Cluster identification and exact workload binding are separate. See [portable discovery](architecture/portable-environment-discovery.md) and [local validation](development/explicit-infrastructure-connections-2026-09-11.md). Real cluster acceptance remains open.
