# MCP Tools

Operational tools are read-only by default. Change planning requires PLAN and remains available while `mcp.read-only=true`; approve/reject require APPROVE and apply requires WRITE, subject to policy and the global read-only guard. Verification requires READ. Identity comes from the authenticated caller, never actor/approver input text.

Administrative operations are bound to **`targetId`** — a registered Keycloak/RHBK
environment. Most tools take it directly; configuration reads resolve it from an
authorized `scopeId`, and change tools from an authorized change record.
Use `keycloak_list_targets` for target-wide grants or `keycloak_list_configuration_scopes`
for the restricted configuration channel. Arbitrary URLs are rejected
by design (SSRF protection).

## Available

| Tool | targetId required | Other arguments | Description |
|------|-------------------|-----------------|-------------|
| `keycloak_list_targets` | no | — | List authorized registered targets (sanitized) |
| `keycloak_get_target` | yes | — | Target metadata (no URLs/secrets) |
| `keycloak_find_targets` | no | `product?`, `environment?` | Filter targets |
| `keycloak_server_info` | yes | — | Product, version, capabilities |
| `keycloak_list_realms` | yes | — | List realms |
| `keycloak_get_realm` | yes | `realm` | Realm details |
| `keycloak_list_clients` | yes | `realm` | Clients (no secrets) |
| `keycloak_get_client` | yes | `realm`, `clientId` | Client details |
| `keycloak_list_configuration_scopes` | no | — | Exact configured scopes authorized for verified caller/client; not provider inventory or installation discovery |
| `keycloak_read_configuration` | configured by scope | `scopeId` | [Contract 1.0](configuration-reads.md): only approved Boolean realm/client facts, pinned IDs, explicit unknowns; separate client/channel grants |
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
| `keycloak_generate_operations_report` | yes | `profile?`, `metricsWindow?` | Sanitized operations report; additive [findingDetails1.0](architecture/operations-reporting.md#report-bound-mcp-finding-details) binds bounded structured findings/evidence to this exact report |
| `keycloak_list_assessment_profiles` | no | — | Built-in profiles |
| `keycloak_list_assessments` | yes | `page?`, `size?` | Assessment history |
| `keycloak_get_assessment` | yes | `assessmentId` | Assessment summary |
| `keycloak_get_latest_assessment` | yes | — | Latest assessment |
| `keycloak_get_findings` | yes | filters / page | Persisted findings (compact) |
| `keycloak_plan_client_update` | yes | semantic allowlisted fields | Plan milestone 0.8 client configuration update |
| `keycloak_plan_update_client_urls` | yes | realm, client, typed URI/origin sets | Plan typed redirect URI/Web Origin replacement |
| `keycloak_plan_update_client_security` | yes | realm, client, typed security/flow settings | Plan PKCE, OAuth/OIDC flow, service-account, and client-authentication changes |
| `keycloak_plan_create_client` | yes | realm, typed allowlisted client fields | Plan secret-free OpenID Connect client creation; disabled by default |
| `keycloak_plan_set_client_enabled` | yes | realm, client, enabled | Plan enabling or disabling an existing client |
| `keycloak_get_change` | no | `changeId` | Get a change lifecycle record |
| `keycloak_list_changes` | yes | status/page | List authorized target change lifecycle records |
| `keycloak_approve_change` | no | `changeId`, actor | Approve an exact plan fingerprint |
| `keycloak_reject_change` | no | `changeId`, actor/reason | Reject a change |
| `keycloak_apply_change` | no | `changeId` | Apply an authorized approved plan |
| `keycloak_verify_change` | no | `changeId` | Re-run read-back verification |

## Planned later

[AGT2](milestones/agt2-access-aware-assistance.md) plans question-driven composition
of approved reads across infrastructure and IAM with shared UI/REST/MCP authorization.
The current AGT1 profile exposes only the report tool, not every tool in this list.
Legacy grants remain target-wide. The additive configuration-read channel has
backend field/resource/client controls and no grants by default; it neither narrows
legacy roles nor implements user/PII access. Its two tools are not added to AGT1's
allowlist and do not enable an external model. Broader client exposure remains gated.

Existing-target installation confirmation currently has REST/UI surfaces only; there is no MCP binding tool. The optional reference agent is specified in [AGT1](milestones/agt1-reference-agent.md), not an implemented model runtime.

| Tool | Planned purpose |
|------|-----------------|
| `keycloak_compare_targets` | Compare two targets |

## Error shape

Tool error messages retain domain codes such as `TARGET_NOT_FOUND`, `TARGET_DISABLED`,
`TARGET_NOT_AUTHORIZED`, `REALM_NOT_FOUND`, `AUTHENTICATION_FAILED` and
`KEYCLOAK_UNAVAILABLE`, followed by filtered explanatory text.

The ten non-change families (targets, server, realms, clients, users, groups,
roles, inventory, metrics and assessments) share `McpToolErrorProjector`.
Recognizable credentials in domain and prebuilt tool-error messages are filtered;
the outgoing exception is a new copy without the original cause or suppressed
exceptions. Unknown checked/runtime failures return the fixed diagnostic
`INTERNAL_ERROR: tool operation failed`, not provider messages or stack traces.
Change tools retain their separately hardened error boundary. Read-only checks,
target authorization and invocation audit/metrics are not bypassed by projection.

## Read-output trust boundary

The [read-metadata slice](development/h1-read-metadata-2026-09-19.md) extends
recognizable-credential filtering to ordinary Admin read DTOs and selected
target, environment, inventory and semantic-metrics outputs. Projection returns
copies: internal collectors, deterministic rules and desired/applied change state
do not consume these lossy presentation values. Declared registered-target and
provenance identity fields retain their canonical values; they are not free-form
metadata to mask or rewrite. Provider resource identifiers (realm/client/user/group/
role IDs or names) that resemble credentials can be masked and become unusable as
follow-up handles; unlike registered target IDs, they are part of the lossy read view.

This does not detect arbitrary unlabeled/encoded secrets or make untrusted text
authoritative. Do not store credentials in names, tags or identities. Installation
identity, candidate confirmation and mandatory audit retain their separate exact
identity contracts; no generic masking or new MCP binding tool is introduced.
The ledger records validation and remaining limits; this is not H1 acceptance or
live RHBK/OpenShift certification.
