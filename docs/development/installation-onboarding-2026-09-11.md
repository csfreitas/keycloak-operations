# Installation candidate confirmation — local slice, 2026-09-11

## Delivered workflow

The **Installation** tab on an existing target now supports scoped candidate discovery, explicit resource selection, a before/after UID review and confirmation. It does not create targets, connections, credentials, namespaces or workloads. It does not provision a cluster or grant permissions.

The target must already be persisted in database/composite registry mode and have an administrator-approved infrastructure type, cluster ID, namespace and credential reference. Configuration-only mode does not accept platform-managed binding changes. Cluster clients continue to use explicit credentials with no ambient fallback.

1. Read current binding/revision and caller capabilities.
2. Request discovery within the target's configured namespace. List Deployment and StatefulSet metadata; discover the Keycloak API group's advertised preferred version for Operator CR candidates. No raw labels, annotations, environment variables, certificates or Secrets are returned. A generic workload candidate does **not** establish that it runs Keycloak or corresponds to the target's Keycloak URL; the operator must verify that association.
3. Store an opaque discovery run and candidate IDs, tied to target, authenticated actor, binding revision and connection-context hash. Runs expire after ten minutes. Nothing is selected or confirmed automatically, even for a single candidate.
4. Confirm only a server-retained candidate, after rechecking permissions, expiry, actor, target, revision, connection context and live resource UID. Missing/recreated/denied resources require a new discovery/review, not silent replacement.
5. Under a target database row lock, persist the new binding and increment its revision. Consume the run and persist a mandatory success audit in the **same transaction**. Audit/persistence failure rolls back the binding. No cluster write occurs.

The UI requires a selection and acknowledgment, resets state on target navigation, discards late results and clears the candidate selection after success or failed confirmation. API security does not depend on that checkbox: permissions, retained candidate IDs, revision and live UID checks are authoritative. Neither the UI nor these controls prove human presence or two-person approval.

## Permissions and read-only policy

- State: `READ`.
- Discovery: `READ` plus new explicit `DISCOVER` permission.
- Confirmation: `READ`, `DISCOVER` and new explicit `BIND`; global `mcp.read-only=true` blocks BIND even when granted.
- `ADMIN`, `WRITE` or `READ` do not implicitly include the new permissions. Local-lab remains an explicitly unauthenticated mode and still respects global read-only.

Example **for administrator review only**, not applied by this work:

```properties
platform.authorization.grants.installation-operator.targets=approved-lab-target
platform.authorization.grants.installation-operator.permissions=READ,DISCOVER,BIND
```

Enabling confirmation also requires the administrator to review disabling global read-only. That global switch affects other already-granted write permissions; do not disable it casually on a shared deployment. No default grant, global mode or credential was changed here. The success audit for binding is mandatory even if optional operational audit logging is disabled.

## REST and storage

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/targets/{targetId}/installation` | Configured binding, revision, source and caller capabilities; no live verification |
| `POST /api/v1/targets/{targetId}/installation/discover` | Scoped discovery; returns run ID, candidate IDs and expiration |
| `POST /api/v1/targets/{targetId}/installation/confirm` | Accepts only `runId` and `candidateId`; no URL, namespace, credential or caller actor input |

Application logic resides in shared services. This slice adds REST/UI adapters, **not** a new MCP confirmation tool.

Flyway V10 adds managed-binding/revision fields to targets and a discovery-run table. One new run replaces earlier runs for the same actor/target. Expired runs are removed when that target is rediscovered; at most 20 retained runs per target and 100 total candidates per run are accepted. Continuation tokens and over-limit lists fail rather than yielding confirmable partial results. Expired records in inactive targets remain until the next discovery; there is no background retention worker yet. These are count/expiry limits, not complete response-byte or distributed rate limits.

Bootstrap preserves a platform-managed binding when target Keycloak URL and infrastructure connection fields remain unchanged. Changing those fields clears the managed binding and increments its revision, requiring reconfirmation. While managed, the platform binding takes precedence over the configuration file's installation fields. Dedicated unbind/revert-to-config UI and a dedicated bootstrap-invalidation audit event remain follow-ups. A configuration file cannot silently overwrite a user-confirmed binding at the next startup.

`cluster-id` remains a configured connection identifier, not cryptographic cluster identity. Discovery context also binds the resolved API-server URL. TLS/credential policy is inherited from the existing explicit connection layer; changing trust policy or rotating credentials under the same reference is not independently revisioned by this slice. Reads and remote UID revalidation are not an atomic cluster snapshot.

## Validation and boundaries

- Starting HEAD `572cb7b`; existing uncommitted work preserved. No commit/push, real cluster access, provisioning or default-permission change.
- Baseline Java 21 via jenv: **338 backend tests passed**, **9 opt-in integrations skipped**, `mvn clean verify` SUCCESS at 2026-09-11 01:39:00 -03:00. Log `/private/tmp/kcops-onboarding-baseline.log`.
- UI baseline: **89 tests passed**, log `/private/tmp/kcops-onboarding-ui-baseline.log`.
- Intermediate validation exposed two test-library API mismatches (Vitest assertion and Fabric8 list builder); tests were adapted to the installed versions. No runtime workaround or dependency upgrade was introduced.
- UI final: **94 tests passed**, production build successful. Logs `/private/tmp/kcops-onboarding-ui-verified.log` and `/private/tmp/kcops-onboarding-ui-build-verified.log`.
- Final backend: **358 tests passed**, 0 failures/errors, **9 opt-in integrations skipped**, `mvn clean verify` SUCCESS at 2026-09-11 01:53:15 -03:00 (1m05s). Log `/private/tmp/kcops-onboarding-final-verified.log`. This slice adds 20 backend tests and five UI tests; V1–V10 applied on fresh transient databases.
- `git diff --check` passed. Final Podman: **0 containers, 0 volumes**, four cached reusable images preserved. No global prune or manual removal of user resources.

Validation uses transient local PostgreSQL, Fabric8 mock APIs and synthetic/mock platform identities. It covers real HTTP serialization/persistence, permission checks, actor isolation, expiry/replay/revision/context rejection, UID replacement, transactional rollback, bootstrap preservation/invalidation and UI target-switch safety. The mandatory-audit rollback test forces a surrounding transaction rollback; it is not a database fault-injection or simultaneous-process concurrency test. Migrations were tested on fresh databases, not an upgrade of a populated customer database. A live browser + real IdP + cluster workflow remains unverified.

Full onboarding (new connections/targets), richer candidate confidence, served-version compatibility across real Operator releases, background retention, unbind/reconciliation UX, host/container collectors and production governance remain open. Next local gate: end-to-end browser/identity workflow with approved local fixtures, then dedicated RHBK/OpenShift validation.
