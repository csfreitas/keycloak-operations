# MCP Tools

All tools in **0.1.0** are read-only. Write operations are not registered while
`mcp.read-only=true`.

Every administrative tool requires **`targetId`** — a registered Keycloak/RHBK
environment. Use `keycloak_list_targets` when unknown. Arbitrary URLs are rejected
by design (SSRF protection).

## Available in 0.1.0

| Tool | targetId required | Other arguments | Description |
|------|-------------------|-----------------|-------------|
| `keycloak_list_targets` | no | — | List registered targets (sanitized) |
| `keycloak_get_target` | yes | — | Target metadata (no URLs/secrets) |
| `keycloak_find_targets` | no | `product?`, `environment?` | Filter targets |
| `keycloak_server_info` | yes | — | Product, version, capabilities |
| `keycloak_list_realms` | yes | — | List realms |
| `keycloak_get_realm` | yes | `realm` | Realm details |
| `keycloak_list_clients` | yes | `realm` | Clients (no secrets) |
| `keycloak_get_client` | yes | `realm`, `clientId` | Client details |
| `keycloak_search_users` | yes | `realm`, `search`, `first?`, `max?` | Search users |
| `keycloak_get_user` | yes | `realm`, `userId` | User details |
| `keycloak_list_groups` | yes | `realm`, `first?`, `max?` | List groups |
| `keycloak_get_group` | yes | `realm`, `groupId` | Group details |
| `keycloak_list_roles` | yes | `realm`, `first?`, `max?` | List realm roles |
| `keycloak_get_role` | yes | `realm`, `roleName` | Role details |
| `keycloak_discover_environment` | yes | — | Runtime discovery (limited in 0.1.x) |

## Planned assessment tools (0.2.0+)

| Tool | Planned purpose |
|------|-----------------|
| `keycloak_run_assessment` | Run a named assessment profile for a target |
| `keycloak_compare_targets` | Compare two targets |
| `keycloak_assess_ha` | High availability assessment |
| `keycloak_assess_security` | Security assessment |
| `keycloak_assess_capacity` | Capacity assessment |

## Error shape

Structured errors include `TARGET_NOT_FOUND`, `TARGET_DISABLED`, `TARGET_NOT_AUTHORIZED`,
`REALM_NOT_FOUND`, `AUTHENTICATION_FAILED`, `KEYCLOAK_UNAVAILABLE`, etc.
Stack traces are never returned to clients.
