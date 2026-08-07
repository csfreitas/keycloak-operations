# Security

## Principles (0.1.0)

1. **Read-only by default** — `keycloak.mcp.read-only=true`; write tools are not registered.
2. **No secrets in tool output** — `ClientDetails` has no secret fields; mappers never copy secrets.
3. **Defense in depth redaction** — `SensitiveDataFilter` recursively redacts maps/lists/beans for keys matching password, secret, token, credential patterns.
4. **No secrets in logs** — audit and log helpers run string redaction before logging.
5. **Least privilege for production** — service accounts should use view/query (or FGAP) roles, not `realm-admin`.

## Authentication to Keycloak

The MCP server authenticates to Keycloak with **OAuth 2.0 client credentials**:

| Property | Env var | Default |
|----------|---------|---------|
| `keycloak.url` | `KEYCLOAK_URL` | `http://localhost:8080` |
| `keycloak.auth-realm` | `KEYCLOAK_AUTH_REALM` | `master` |
| `keycloak.client-id` | `KEYCLOAK_CLIENT_ID` | `keycloak-mcp` |
| `keycloak.client-secret` | `KEYCLOAK_CLIENT_SECRET` | `change-me` |

Store credentials in Kubernetes/OpenShift Secrets (see `deploy/openshift/50-secret.yaml`
template — placeholders only in git).

## Local demo privilege warning

`scripts/setup-dev.sh` assigns broad roles including `realm-admin` for developer
convenience. That is **DEV ONLY**. Production deployments must:

- Prefer Fine-Grained Admin Permissions (FGAP) where available
- Or assign only `view-*` / `query-*` realm-management roles required by read tools
- Never commit real client secrets

## OpenShift RBAC and Secrets

The assessor `ClusterRole` includes `get/list/watch` on `secrets` so collectors can
correlate Secret *metadata*. In Kubernetes, authorized `get`/`list` on Secret
objects still returns `.data`. The application **must** redact secret values via
`SensitiveDataFilter` and should prefer namespace-scoped Roles when possible.

## Network and pod hardening

Deploy manifests set:

- `runAsNonRoot: true`
- `readOnlyRootFilesystem: true`
- `allowPrivilegeEscalation: false`
- drop all capabilities
- NetworkPolicy limiting ingress/egress

## Sensitive key detection

Keys are sensitive (case-insensitive) when they match exact names such as
`password`, `clientSecret`, `secret`, `token`, `accessToken`, `refreshToken`,
`privateKey`, `credentials`, or contain `secret`, `password`, or `credential`.
Metadata such as `name` and `namespace` is preserved.

## Assessment / MCP output

Assessment MCP tools return **compact** DTOs (scores, finding ids, evidence *keys*
only) and always pass through `SensitiveDataFilter`. Full evidence maps are not
dumped to LLM tool responses.
