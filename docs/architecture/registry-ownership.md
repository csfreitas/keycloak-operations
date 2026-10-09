# Registry ownership and configuration reconciliation

Status: ONB1 foundation, not managed registration. [ADR 0014](../adr/0014-registry-ownership-before-managed-writes.md)
defines the decision; [preflight 0.1.0](../registry-preflight.md) remains unchanged.

## State and responsibilities

| Field/state | Responsibility |
|---|---|
| `registry_owner=CONFIGURATION` | A target explicitly created by this version's trusted server-configuration bootstrap; permits later configuration reconciliation |
| `registry_owner=LEGACY_UNCLASSIFIED` | Historical or otherwise unclassified row; matching config/ID cannot claim it |
| `registry_revision` | JPA `@Version` for ORM row concurrency, beginning at 0; not a human approval or exposed REST revision contract |
| `installation_managed` | Existing confirmed binding takes precedence over the configured binding while connection context remains equal; not ownership of the whole target |
| `installation_revision` | Existing binding/context counter used to reject stale discovery runs; distinct from the registry row version |

Only the two owner states above exist. API-managed/CR-managed ownership, ownership
transfer, registration, activation and their endpoints are not implemented. The
generic persistence mapper defaults to unclassified; it does not infer origin.
Ordinary reads preserve existing enabled/grant checks. An unclassified row is not
automatically disabled or deleted, and its already authorized installation-confirmation
path retains its current policy. Definition ownership and binding authority differ.

## Reconciliation

`TargetBootstrapService.syncConfigTargetsToDatabase()` is one transaction, processing
configured IDs in stable order under target row locks:

1. Missing row: insert with explicit CONFIGURATION owner and audit.
2. Existing CONFIGURATION: compute normalized persisted desired state, including
   reserved management-URL tags and observability defaults, then apply binding precedence.
3. Identical effective state: no update, timestamp/revision change or audit event.
4. Changed effective state: update, flush the ORM row version, persist mandatory audit.
5. Unclassified/invalid owner: fixed ownership-conflict failure, without takeover.
   Earlier changes/audit in the same synchronization roll back.

Removing a target from configuration does not delete, disable or transfer the retained
row. Changing its ID does not rename or migrate its history. Such lifecycle actions
need explicit later contracts. The method returns the number of **changed** targets,
not the number inspected. Concurrent creation of the same missing ID still relies on
the primary key: a loser fails/rolls back, with no unbounded automatic retry.

Keycloak URL, auth realm, client ID, credential reference and management URL, plus
infrastructure type/cluster ID/namespace/credential reference, define the connection
comparison for binding preservation. A change clears a previously managed binding,
sets managed=false and increments installation revision **only when the desired
configuration has no explicit binding**. A connection change combined with an explicit
configured binding is rejected before mutation; remove the ambiguous configured
binding first. This prevents the next identical restart from reapplying a cleared
binding. Changing an effective non-managed configured binding also increments that
revision. Pending runs with the old binding revision
cannot confirm; no run or historical evidence is rewritten/purged.

Name/product/environment/enabled/ordinary-tags/observability changes update the registry row
when effective state changes; they do not alone establish changed physical identity.
The reserved `management-url` tag is an exception because it is a Keycloak connection
field after normalization, not ordinary display metadata.
Credential-value rotation under an unchanged reference is not detected by this
comparison. Discovery/confirmation still performs its existing current authorization,
context and live UID checks; a registry row version alone does not pin every future
registration/security policy.

## Atomic audit

Audit uses existing source **SYSTEM**, tool `registry.bootstrap`, operation
`TARGET_CONFIG_CREATED` or `TARGET_CONFIG_UPDATED`, status SUCCESS and exact target ID.
Metadata includes `origin=CONFIGURATION_BOOTSTRAP`, `actorKind=SYSTEM`, owner, previous/
new registry and installation revisions, `bindingInvalidated` and fixed field names.
Previous registry revision is null for creation. No raw endpoints, references, old/
new values, free-form tag keys, credential values or synthetic human actor are retained.
Field names may identify a reference field but do not contain its value.

Audit writes are mandatory even with `platform.audit.enabled=false`. Failure rolls
back definition, binding, versions and audit in the same transaction. No-op produces
no event; rejected synchronization does not retain misleading success records. Failed
attempt telemetry/attributed operator registration and approval are separate work.
Existing target-scoped audit reads and response redaction remain in force.

## Registry modes and failure behavior

| Mode | Authority |
|---|---|
| `configuration` / `config` | Explicit static configuration; no database bootstrap |
| `database` / `db` | Database after successful configuration bootstrap |
| `composite` (default) | Same database authority after bootstrap; no empty/error fallback |

Initialization errors surface a fixed cause-free diagnostic; no endpoint/SQL/provider
details are copied into the new registry diagnostic. An absent ID stays absent, an
empty database stays empty and a disabled row stays present for ordinary authorization
to deny. None resurrects a configured namesake. This is registry initialization and
resolution behavior, not a new platform readiness probe or claim of database HA.

## V11 migration and safe upgrade limits

V11 adds both non-null columns, defaults every old row to LEGACY_UNCLASSIFIED/0 and
constrains owner names and nonnegative revisions. It does not infer provenance from
matching config, rewrite existing fields/timestamps, classify managed bindings, remove
history or grant access. Tests cover fresh startup and a populated **synthetic** V10
schema upgraded in disposable PostgreSQL; they are not a customer restore rehearsal.

**Do not deploy this development migration to a populated installation without
reviewing ownership first.** A configured ID colliding with an old row now blocks
sync/registry initialization. This deliberately preserves data; the sanctioned
adoption/transfer workflow is not delivered. Do not bulk-set owners, delete/recreate
targets, disable audit or select configuration mode to bypass the conflict.

Before production upgrade: inventory the intended owner of each record with approved
evidence, back up the database, review collisions and retained history, and accept a
governed adoption/restore procedure. No automated adoption command is provided here.
V11 is not undone by changing the image. Older binaries/raw SQL ignore application
ownership and `@Version`; mixed-version writers and image-only rollback are unsupported.
Database constraints alone are not per-actor access control or an audit guarantee.

Next ONB1 slice: governed legacy adoption and config/API owner transfer, source/expected
revision contracts, separately authorized transactional registration, bootstrap/grant
audit and destination approval. Do not infer that registry writes can bypass global
read-only; no administrative write policy or public mutation endpoint changed here.
