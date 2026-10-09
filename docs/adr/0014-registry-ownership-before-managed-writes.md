# ADR 0014 — Registry ownership before managed writes

- **Status:** Accepted for the configuration reconciliation foundation
- **Date:** 2026-10-09

## Context

ONB1 preflight does not register targets. The existing startup synchronizer could,
however, overwrite any database row sharing a configured ID and then hide a failed
sync behind configuration fallback. `installationManaged` describes only the binding,
not ownership of the complete target. Adding registration on this foundation would
permit conflicting writers and silent takeover. Restart also updated timestamps even
when the effective target did not change.

## Decision

Add Flyway **V11**, without changing historical migrations or target IDs: closed
`registry_owner` (`LEGACY_UNCLASSIFIED`, `CONFIGURATION`) and nonnegative
`registry_revision`, mapped to JPA `@Version`. Every preexisting row starts unclassified
at revision zero. Only a new row inserted by configuration bootstrap is explicitly
CONFIGURATION. Matching names/content, a prior managed binding or current grants do
not prove ownership. No API/CR owner state or automatic adoption is introduced.

The configuration synchronizer locks targets in ID order. It rejects ownership
conflicts before overwriting a row and reconciles only actual changes to the effective
persisted representation. No-op leaves timestamps, revisions, binding and audit
unchanged. A confirmed binding retains precedence while its connection context is
unchanged; changed Keycloak connection or infrastructure scope invalidates it and
advances the separate installation revision. Changing a managed connection while
also supplying an explicit configured binding is rejected: otherwise a later restart
could silently reapply the cleared binding. Remove that ambiguous configured binding
before changing connection; later selection remains separately reviewed. Names and
ordinary tags alone do not imply a new physical installation; reserved `management-url`
is a connection field. Credential values are never resolved to compare configuration.

Creation/change and mandatory system audit share one transaction. Flush observes
the real ORM row version before audit; a later conflict or audit failure rolls back
the entire synchronization. Audit includes origin, fixed changed-field names and
revisions, not before/after values, endpoints, credential references or a fictitious
human approver. Optional operational-audit settings do not suppress it.

After successful bootstrap, `database` and `composite` use the database exclusively.
Failure, absence or an empty database never selects a different configured target.
Explicit `configuration` mode remains separate and does not run database bootstrap;
it must not be presented as a recovery procedure for an ownership conflict.

Existing target READ/DISCOVER/BIND and global read-only remain unchanged. Static
configuration reconciliation is not an administrative REST grant. Preflight 0.1.0
is unchanged and still has no persistence/network effect. Row version does not add
an optimistic REST edit API or change the public installation revision contract.

## Alternatives and consequences

- Inferring configuration ownership by ID/equal content would silently adopt foreign
  records; rejected. Defaulting all old rows to CONFIGURATION has the same defect.
- Reserving MANAGED/CR owners without a writer/lifecycle contract adds unsupported
  states; defer them until the separate registration/ownership-transfer slice.
- Keeping fallback after ownership/audit failures can bypass the chosen source;
  rejected. Explicit configuration-only operation remains supported.
- **Compatibility gate:** populated V10 databases with configured-ID collisions will
  block reconciliation after V11. Their data is preserved, not auto-adopted. Reviewed
  adoption/restore is required before a production upgrade, not a bulk owner update.
- V11 is additive, not automatically reversible. Old binaries/raw SQL do not honor
  the new application ownership/version checks; mixed-version writers and image-only
  rollback are unsupported. Backups and an accepted data recovery plan are required.
- This is tested against disposable PostgreSQL only. No customer/local retained DB
  is migrated, and no real cluster/Operator acceptance or release readiness is implied.

See [ownership contract](../architecture/registry-ownership.md),
[ONB1](../milestones/onb1-environment-registry.md) and
[evidence](../development/onb1-ownership-2026-10-09.md).
