# MCP Tools

Operational tools are read-only by default. Change planning is a dry run and remains available while `mcp.read-only=true`; approve, reject, and apply are gated by write authorization, policy, approval, and the global read-only setting.

Every administrative tool requires **`targetId`** — a registered Keycloak/RHBK
environment. Use `keycloak_list_targets` when unknown. Arbitrary URLs are rejected
by design (SSRF protection).

## Available

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
| `keycloak_get_metrics_status` | yes | — | Metrics provider status |
| `keycloak_get_performance_summary` | yes | `window?` | Compact HTTP/DB/JVM summary |
| `keycloak_get_metrics` | yes | `category`, `window?` | Semantic category metrics |
| `keycloak_run_assessment` | yes | `profile?` | Run + persist assessment (compact) |
| `keycloak_health_check` | yes | — | Lightweight health check |
| `keycloak_generate_operations_report` | yes | `profile?`, `metricsWindow?` | Sanitized platform, health, assessment, findings, and performance report |
| `keycloak_list_assessment_profiles` | no | — | Built-in profiles |
| `keycloak_list_assessments` | yes | `page?`, `size?` | Assessment history |
| `keycloak_get_assessment` | yes | `assessmentId` | Assessment summary |
| `keycloak_get_latest_assessment` | yes | — | Latest assessment |
| `keycloak_get_findings` | yes | filters / page | Persisted findings (compact) |
| `keycloak_plan_client_update` | yes | semantic allowlisted fields | Plan milestone 0.8 client configuration update |
| `keycloak_plan_update_client_urls` | yes | realm, client, typed URI/origin sets | Plan typed redirect URI/Web Origin replacement |
| `keycloak_get_change` | no | `changeId` | Get a change lifecycle record |
| `keycloak_list_changes` | optional filter | status/page | List change lifecycle records |
| `keycloak_approve_change` | no | `changeId`, actor | Approve an exact plan fingerprint |
| `keycloak_reject_change` | no | `changeId`, actor/reason | Reject a change |
| `keycloak_apply_change` | no | `changeId` | Apply an authorized approved plan |
| `keycloak_verify_change` | no | `changeId` | Re-run read-back verification |

## Planned later

| Tool | Planned purpose |
|------|-----------------|
| `keycloak_compare_targets` | Compare two targets |

## Error shape

Structured errors include `TARGET_NOT_FOUND`, `TARGET_DISABLED`, `TARGET_NOT_AUTHORIZED`,
`REALM_NOT_FOUND`, `AUTHENTICATION_FAILED`, `KEYCLOAK_UNAVAILABLE`, etc.
Stack traces are never returned to clients.
