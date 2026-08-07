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
| `keycloak_discover_environment` | yes | — | Runtime discovery |
| `keycloak_get_inventory` | yes | — | Sanitized infrastructure inventory |
| `keycloak_run_assessment` | yes | `profile?` | Run + persist assessment (compact) |
| `keycloak_health_check` | yes | — | Lightweight health check |
| `keycloak_list_assessment_profiles` | no | — | Built-in profiles |
| `keycloak_list_assessments` | yes | `page?`, `size?` | Assessment history |
| `keycloak_get_assessment` | yes | `assessmentId` | Assessment summary |
| `keycloak_get_latest_assessment` | yes | — | Latest assessment |
| `keycloak_get_findings` | yes | filters / page | Persisted findings (compact) |

## Planned later

| Tool | Planned purpose |
|------|-----------------|
| `keycloak_compare_targets` | Compare two targets |

## Error shape

Structured errors include `TARGET_NOT_FOUND`, `TARGET_DISABLED`, `TARGET_NOT_AUTHORIZED`,
`REALM_NOT_FOUND`, `AUTHENTICATION_FAILED`, `KEYCLOAK_UNAVAILABLE`, etc.
Stack traces are never returned to clients.
