# ONB1 — Connection and environment registration

Status: **PARTIAL — preflight and configuration-ownership foundation**; extends incomplete historical 0.8.2. Owner: development + security/platform reviewer. No new calendar commitment; not required for a demo using preconfigured targets.

## Current foundation — 2026-10-09

[Ownership contract](../architecture/registry-ownership.md) and
[ADR 0014](../adr/0014-registry-ownership-before-managed-writes.md) add V11 conservative
legacy ownership, separate ORM row revision, normalized no-op reconciliation and
mandatory transactional system audit. Database/composite never uses a configuration
fallback. This protects future registration but does not create it. All populated
upgrades with legacy/config ID collisions require reviewed adoption, still pending;
no live/retained database was migrated. [Evidence](../development/onb1-ownership-2026-10-09.md)
distinguishes disposable migration/concurrency/rollback tests from production acceptance.

## Previous delivered subset — administrative preflight, 2026-10-09

[Contract 0.1.0](../registry-preflight.md) and
[ADR 0013](../adr/0013-registry-preflight-before-registration.md) introduce default-closed
REST preflight with exact administrator/client matching, bounded closed draft parsing,
local ID/reference checks and explicit inconclusive/no-connectivity semantics. No
registration, owner/revision model or audited bootstrap was included in that earlier
preflight slice. UI/MCP, remote checks, grants and global read-only/BIND remain unchanged.
[Evidence](../development/onb1-preflight-2026-10-09.md)
records unit and synthetic HTTP validation separately from live OIDC/provider gates.
All whole-milestone criteria below remain open.

Next: specify governed adoption/config-to-API owner transfer and expected-revision
contracts, then review transactional registration and the initial-admin/grant workflow.
Do not add network validation or persistence to preflight implicitly. Real OIDC
admission, rate/time budgets and destination approval need their own evidence.

## Objective and dependencies

Register approved connections and logical environments without source edits, then reuse exact installation confirmation. Depends on H1/D1 contracts; existing V9/V10 binding is reused, not rewritten. See [portable discovery](../architecture/portable-environment-discovery.md) and [persistence](../architecture/persistence.md).

Roadmap: portable onboarding, GOV-01, historical 0.8.2. Requirements: FR-TARGET-001–004, FR-DISC-001–004, FR-INV-003, FR-ONBOARD-001/002/003, SEC-TARGET-001/002, SEC-INFRA-004/005, SEC-CRED-001–004, SEC-AUTHZ-001.

## Implementation slices

1. Design connection/environment revisions and separately authorized administrator registration; endpoints are approved configuration, never arbitrary operational tool inputs. Define validation against SSRF, redirects, credential misuse and scope changes before API/UI exposure.
   Define explicit configuration/API ownership, stable IDs and reviewed ownership
   transfer. A future declarative adapter uses the same versioned administrative
   policy/API, never direct database writes or bypassed confirmation/audit. Resolve
   the current global read-only/BIND gate explicitly before enabling administrative
   registration in the [OP1 read-only installation](op1-operator-installation.md);
   no automatic switch-off or implicit remote-write authority.
   Specify an auditable first-administrator bootstrap and governed grant channel for
   an empty installation; authentication, CR creation and registration alone grant
   no administrative or READ/ASSESS permissions. No arbitrary-property bootstrap bypass.
2. Add safe REST/UI lifecycle using secret references only; connection checks expose sanitized capability/error states and bounded timeouts. No automatic target grants or network scan.
3. Add managed-binding reconciliation/unbind/revert-to-config, explicit before/after review and mandatory audit, including bootstrap invalidation.
4. Add discovery expiry cleanup and count/byte/concurrency limits; migrate populated records without losing managed binding or history.

## Exit criteria

- [ ] Two approved connections with identical namespace/resource names remain isolated; invalid endpoint/credential/scope changes cannot reuse prior confirmation.
- [ ] Authorized operator creates/validates an environment without source edits; unauthorized caller cannot register or widen scope.
- [ ] Credential values never enter database, API, audit or browser; a failed validation grants no access.
- [ ] Concurrent revision changes, rollback, expired runs, restore/migration and config/managed precedence tests pass.
- [ ] Operators can reconcile a replaced resource without silent rebinding; deletion/retention consequences documented before enabling lifecycle actions.
- [ ] Configuration/API ownership and read-only registration semantics are explicitly
  accepted/tested. Future CR-managed records reject conflicting UI edits; source
  revision and owner transfer invalidate stale confirmation, preserve remote resources
  and follow reviewed retention. Registry CRDs are not required for the initial API slice.

## Exclusions

No provisioning, broad cloud discovery, arbitrary shell, resource cleanup, credential-management UI or mass import without a separately reviewed slice. Changes to global read-only semantics require an ADR and negative authorization tests.
