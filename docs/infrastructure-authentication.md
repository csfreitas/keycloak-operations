# Infrastructure authentication

## Modes

| Mode | When | Config |
|------|------|--------|
| `IN_CLUSTER` | No `credential-ref`, or empty token/kubeconfig | ServiceAccount mounted in the pod |
| `TOKEN` | `mcp.credentials.<ref>.token` set | `token` + `api-server-url` + optional `ca-cert-data` |
| `KUBECONFIG` | `mcp.credentials.<ref>.kubeconfig` set (path) | File path on the Operations Platform host |

## TLS

- Default: TLS verification **enabled** (`trust-insecure=false`)
- `trust-insecure=true` only for local/dev (e.g. CRC with self-signed API)
- Never the production default

## Isolation

- Clients are cached per `targetId` + fingerprint (URL, namespace, token hash, trust flag)
- Target A credentials cannot be reused for Target B through the cache
- LLM never supplies API URL, token, kubeconfig, or CA
