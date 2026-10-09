# ADR 0013 — Administrative preflight before registry writes

- **Status:** Accepted for the local preflight slice; registration remains planned
- **Date:** 2026-10-09

## Context

ONB1 needs an administrative boundary before an empty OP1 installation can register
connections. Existing target ADMIN grants require an existing target; configuration
bootstrap can overwrite database records, and global read-only blocks BIND. Neither
implicit first-login administration nor disabling read-only is an acceptable shortcut.

## Decision

Add REST-only `POST /api/v1/registry/targets/preflight`, independently versioned
contract **0.1.0**, disabled by default. Explicit server configuration pins each
administrator's verified JWT issuer, subject, required role and allowed REST clients
(`azp`). All must match the same entry in authenticated mode. Existing target grants,
local-lab mode and MCP client metadata confer no authority. Changes to the configured
administrator list require restart; no hot-revocation claim is made.

The service authorizes before parsing the bounded closed draft or consulting local
metadata. It checks syntax, exact target-ID collision across configuration/database
and credential-reference membership, never resolves credentials or contacts a
candidate endpoint. PostgreSQL reads are platform I/O, not target connectivity checks.
Unavailable registry reads remain inconclusive; outputs contain only fixed codes
and limitations, never submitted URLs, references, identities or values.

This is a narrow clarification of [ADR 0004](0004-no-arbitrary-endpoints-from-mcp.md):
operational REST/MCP still uses registered bindings only. An authorized administrative
draft may contain a candidate URL **as inert data**, not as permission to issue HTTP,
DNS, discovery or validation calls. No new MCP tool is added. Future network checks
require a separate reviewed destination/redirect/credential policy.

There are no writes, target grants, persistent audit events, first-admin bootstrap
workflow, connection entity, schema migration or registration token. A result is a
point-in-time local check, not approval or a reservation. `mcp.read-only=true` remains
unchanged; preflight is not BIND/ADMIN and does not change their authorization.

## Consequences

- Operators can exercise the contract before ownership, revisions and audited writes
  are designed; [ONB1](../milestones/onb1-environment-registry.md) stays partial.
- The initial HTTPS/identifier syntax subset is intentionally narrower than existing
  static configuration. No existing target is rewritten or invalidated.
- HTTP/authenticated boundary tests, defaults and negative policy tests accompany
  implementation; synthetic identities do not prove real OIDC or customer acceptance.
- Global request deadlines, concurrency/rate limits, audited administration, conflict
  handling and governed bootstrap remain prerequisites for broader lifecycle work.
- [Contract and limits](../registry-preflight.md) are authoritative for this slice;
  [evidence](../development/onb1-preflight-2026-10-09.md) separates execution from design.
