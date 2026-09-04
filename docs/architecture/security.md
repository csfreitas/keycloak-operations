# Security

## Principles

1. **Read-only by default** — `mcp.read-only=true`; controlled writes require target write authorization, policy, approval, apply, and verification.
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

The assessor `ClusterRole` deliberately omits Kubernetes `secrets`. Collectors do not require or return Secret contents. Prefer namespace-scoped Roles when cluster-wide inventory is not required.

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

Operations reports enforce `ASSESS` authorization, sanitize both structured and Markdown output, and replace provider failures with safe section statuses instead of returning raw exception messages. AI callers cannot supply provider credentials, endpoints, arbitrary Admin REST, arbitrary PromQL, or policy decisions.
