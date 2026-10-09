# Audit

## Modes (`platform.audit.mode`)

| Mode | Behavior |
|------|----------|
| `METADATA` | Persist ids, tool, status, duration only (no params) |
| `SANITIZED` | Default — params retained after metadata redaction |
| `FULL` | Same mandatory recognizable-credential filtering; FULL does not bypass it |

Disable optional operational DB audit persistence with `platform.audit.enabled=false` (logs still emitted). This does **not** disable mandatory installation-confirmation or configuration-reconciliation success audit.

## Operational metadata boundary

`AuditService` applies `SensitiveDataFilter.redactMetadata` to parameter payloads in
both `SANITIZED` and `FULL` modes; `METADATA` omits them. The persistence mapper
independently filters free-form params/metadata and recognizable credentials in tool
and operation text. Filtering covers supported credential keys, nested JSON-shaped
string values and dynamic keys, not just top-level secret field names.

Canonical event/trace/target IDs, source, status and timestamps retain their original
correlation and scope semantics. They are not replaced by lossy display values in the
database. Log rendering uses separately filtered copies, including realm/request text,
without changing the identifiers used for target selection or audit correlation.

Historical query responses filter tool/operation text and metadata through copies;
stored rows are not rewritten, and params are not exposed by `AuditEventSummary`.
This does not certify that historical database bytes were secret-free. Optional
operational persistence remains best-effort: its failure warning is fixed text and
does not append provider messages or exception causes. The mandatory transactional
installation audit below is unchanged.

Recognition is limited to the formats supported by `SensitiveDataFilter`; arbitrary
unlabelled/encoded secrets and other legacy tool/error paths remain separate review
items. Metadata redaction is not an authorization control or universal prompt-injection
defense. See the [change/audit metadata ledger](development/h1-change-metadata-2026-09-19.md)
for scoped validation and remaining limits.

## Mandatory installation audit

Installation confirmation records trusted actor, target, discovery run, selected/previous UID and binding revision. Binding update, run consumption and audit share one transaction; failure rolls them back together. This is separate from best-effort `AuditService` events and does not inherit optional enablement/mode settings. It does not prove two-person approval. See [installation confirmation](development/installation-onboarding-2026-09-11.md).

## Mandatory configuration reconciliation audit

Configuration-owned creates/changes now use source SYSTEM, tool `registry.bootstrap`,
operations `TARGET_CONFIG_CREATED` / `TARGET_CONFIG_UPDATED` and metadata origin
`CONFIGURATION_BOOTSTRAP`. Metadata includes fixed changed-field names, previous/new
registry/binding revisions and binding invalidation, not field values, URLs,
credential references or a human actor. Target ID remains exact for access scoping.
No-op emits no event. Audit failure or later ownership conflict rolls back the whole
sync, including earlier success events; optional modes do not bypass this. This is
not an audited first-administrator bootstrap or public registration workflow.
See [contract](architecture/registry-ownership.md) and
[evidence](development/onb1-ownership-2026-10-09.md).

## Sources

`MCP`, `WEB`, `REST`, `SCHEDULED`, `SYSTEM`

MCP tool calls continue to use `AuditService.logToolInvocation(...)`, which records
`AuditSource.MCP` when persistence is enabled.

## Query

`GET /api/v1/audit?targetId=&source=&page=&size=`

Queries require an authenticated session and target `READ`. An explicit target is
authorized before lookup; without one, the query is restricted to the caller's readable
targets. Filtering takes place in the database before pagination and totals, so global
or unbound events are not exposed through this endpoint. Presentation filtering does
not alter that target set.
