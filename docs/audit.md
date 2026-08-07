# Audit

## Modes (`platform.audit.mode`)

| Mode | Behavior |
|------|----------|
| `METADATA` | Persist ids, tool, status, duration only (no params) |
| `SANITIZED` | Default — params redacted via `SensitiveDataFilter` |
| `FULL` | Params retained after secret-key redaction |

Disable DB persistence with `platform.audit.enabled=false` (logs still emitted).

## Sources

`MCP`, `WEB`, `REST`, `SCHEDULED`, `SYSTEM`

MCP tool calls continue to use `AuditService.logToolInvocation(...)`, which records
`AuditSource.MCP` when persistence is enabled.

## Query

`GET /api/v1/audit?targetId=&source=&page=&size=`
