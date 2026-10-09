# KeycloakOperations installation contract — draft 0.1

Status: **design only**, 2026-10-09. No CRD, controller, bundle or served API exists.
The example uses a reserved `.example` API group and non-deployable placeholders.
Do not apply it to a cluster. Draft 0.1 is a document revision, `v1alpha1` a proposed
Kubernetes API version, neither a product release nor a compatibility commitment.

Decision: [ADR 0012](../adr/0012-operator-managed-portable-platform.md).
Boundaries: [architecture](operator-managed-platform.md).
Implementation/acceptance: [OP1](../milestones/op1-operator-installation.md).

## Scope and first-release invariants

One namespaced `KeycloakOperations` instance per namespace. The CR describes the
Operations installation only, not the observed Keycloak servers or cluster access.
The final project-controlled API group and schema must be fixed before CRD creation.
Unknown spec fields are rejected; no extension/passthrough map in the first contract.

The initial operand has **one backend replica**, OIDC enabled, global read-only
enabled and no inherited local-lab targets or default assessment grants. UI is a
separate unprivileged component. No database, IdP, collector or evaluated Keycloak
is provisioned. External PostgreSQL and Identity A are required dependencies.

First installation requires a dedicated database in a supported initial state.
Pre-existing platform records or unsupported schema/ownership block installation
pending an approved migration/adoption procedure; never truncate or silently attach
them. Reconciliation/restart of the same verified installation must preserve its
populated database. A future backend bootstrap contract must attest the installation
identity and schema state; the controller does not gain direct SQL administration.

Installing an empty registry can establish platform availability, not operational
readiness or access to any target. ONB1 delivers separately authorized registration.
Current BIND requires global read-only to be disabled; this contract does **not**
change that rule or expose a switch to bypass it. Before ONB1-based onboarding can
be used in this read-only installation, its administrative write policy must be
explicitly designed/accepted and tested, separately from remote-write authorization.
OP1 must not claim a usable onboarded-fleet workflow while that gate is unresolved.
The same integration gate includes explicit, auditable bootstrap of the first
authorized administrator and a governed channel for READ/ASSESS grants. ONB1
registration must not grant those permissions automatically; neither CR creation
nor authentication alone authorizes platform administration.

## Proposed desired-state fields

All fields below are required unless marked optional. Values in the example are
synthetic. Endpoint configuration is administrator-controlled, never an MCP input.

| Field | Type / constraint | Responsibility |
|---|---|---|
| `metadata.name`, `metadata.namespace` | Kubernetes names; one installation/namespace | Unique ownership; no second namespace field or cross-namespace override |
| `spec.release.version` | Exact approved release identifier | Controller resolves backend/UI immutable digests from a reviewed release catalogue; no `latest`, floating ranges or arbitrary image |
| `spec.replicas` | Integer, exactly `1` | Reject horizontal scaling until separately accepted state/session/job design |
| `spec.operationalMode` | Exactly `ReadOnly` | Render/enforce existing global read-only; no implication that configuration mutation is allowed |
| `spec.identity.issuer` | HTTPS URL without userinfo/query/fragment, approved host/path | Exact Identity A issuer; no development-profile fallback |
| `spec.identity.audience` | Nonempty expected backend audience | Reject unintended tokens; never substitute a target credential |
| `spec.identity.backendClientId` | Nonempty backend OIDC client identifier | Independent of audience; never infer one from the other |
| `spec.identity.uiClientId` | Nonempty public PKCE client identifier | Console login client, not a secret |
| `spec.identity.backendClientSecretRef.name` | Optional same-namespace Secret name | Fixed `client-secret` key, only if accepted backend OIDC mode requires it |
| `spec.database.existingConnectionSecretRef.name` | Same-namespace Secret name | Fixed `jdbc-url`, `username`, `password` keys; PostgreSQL only, approved TLS connection policy |
| `spec.exposure.apiHost` | Approved DNS host, distinct from UI host | HTTPS REST/MCP/SSE endpoint |
| `spec.exposure.uiHost` | Approved DNS host, distinct from API host | HTTPS console; configure exact origins and PKCE callback/logout URLs |
| `spec.exposure.tls.mode` | `Reencrypt` for initial OpenShift scope | Both public and backend TLS paths validated, not edge-only trust |
| `spec.exposure.tls.certificateSecretRef.name` | Optional same-namespace Secret name | Fixed `tls.crt`, `tls.key`; absent means approved cluster ingress certificate, not plaintext |
| `spec.trust.caBundleConfigMapRef.name` | Optional same-namespace ConfigMap name | Fixed `ca-bundle.crt`; adds approved public CA material, never disables verification |

The CA bundle covers the installation's approved outbound trust requirements; it
does not silently change target connection trust. Route backend service certificates/
destination trust are managed explicitly by the controller's platform adapter.
Management/health listeners remain private. Ingress/Route implementation and Secret
key mapping require acceptance against the selected OpenShift/product versions.

No free-form `env`, `podTemplate`, JVM arguments, raw Quarkus properties, arbitrary
volume/host paths, target list, connection credentials, role grants or Keycloak CR
fields are admitted. Production sizing/quotas and additional exposure modes need a
reviewed contract extension rather than hidden escape hatches. Initial approved
release defaults for requests/limits are documented and tested within OP1.

## Configuration ownership

| State | Authoritative writer | Conflict / lifecycle rule |
|---|---|---|
| Installation spec | Approved installation administrator or GitOps | UI cannot edit these settings; controller never rewrites spec to hide failure |
| Owned Deployments/Services/Routes/policies | Installation controller | Reconcile only matching owner UID; foreign names fail, never auto-adopt |
| Referenced Secret/ConfigMap, external DB/IdP | Existing owner/secret-management workflow | Validate scoped references; do not overwrite, copy to reports or add destructive ownerReferences |
| Operational registry | ONB1 administrative API/policy | IDs/revisions/confirmation/audit remain authoritative; installation cannot auto-grant access |
| Future CR-managed registry records | Future declarative adapter using that same API | Explicit owner; conflicting UI edits rejected; reviewed transfer/deletion, not two writers |
| Evidence/history and CR status | Backend and controller respectively | Observed state, not desired configuration; sanitized, access-controlled projections |

Bootstrap configuration versus managed database records must have explicit
precedence/migration before importing existing installations. Missing fields or a
new CR never imply deletion of targets, bindings or history. The controller cannot
issue SQL to create targets or sidestep the backend's policy.

## Reconciliation, credentials and upgrades

1. Authorize the administrator; validate closed schema, namespace uniqueness,
   reference syntax and approved release before changing workloads.
2. Resolve only declared same-namespace references with bounded sizes/types. Secret
   values never enter spec/status/events/logs or public checksum annotations. Grant
   the controller only required reference access; installation editing is privileged.
3. Render owned resources with separate installer/backend/UI accounts. Backend has
   no installation/RBAC management rights; no target cluster access is implicit.
4. Reconcile idempotently and record generation-scoped progress. Invalid configuration
   preserves the last accepted workload configuration; changed/invalid credentials
   must not cause fallback to old or broader source credentials. Authentication fails
   closed while the dependency is unavailable.
5. A release change explicitly requests that exact application version. It does not
   approve a different digest. Operator upgrades preserve the selected operand version.
   Block an unsupported version transition or migration before starting the rollout.
6. For database-changing transitions, require a reviewed, single-executor migration
   procedure, verified backup/restore preconditions and maintenance acknowledgement
   bound to the exact transition. The first contract exposes no reusable approval
   boolean; such transitions stay blocked until the approval contract is implemented.
7. Rotation/reconfiguration follows the actual supported reload/restart path and must
   be tested. One replica means interrupted sessions/streams during rollout; clients
   reconnect and reauthenticate as applicable. No zero-downtime or HA promise.

Current implementation gaps: the UI OIDC values are build-time configuration; runtime
configuration must be implemented or a matching verified UI artifact selected before
the CR can configure it. Backend Flyway currently runs at startup; this is not a
validated coordinated upgrade protocol. Initial TLS re-encryption also needs a tested
backend/service certificate path; current edge templates do not supply this contract.
No catalogue of compatible published application releases is supplied by this draft.

## Status contract

Proposed fields: `status.observedGeneration`, `status.appliedRelease` (version and
verified backend/UI digests), and `status.conditions[]`. Each condition has `type`,
`status` (`True`, `False`, `Unknown`), `reason`, sanitized `message`,
`observedGeneration` and `lastTransitionTime`. Reasons are stable machine codes.

| Condition | Meaning, never a target assessment |
|---|---|
| `Accepted` | Desired installation specification/reference policy accepted; not proof of a running system |
| `Progressing` | A bounded reconciliation/rollout is in progress |
| `Available` | Required platform components and essential dependencies serve the expected authenticated interface |
| `Degraded` | Installation failure or incomplete platform capability requiring attention |
| `UpgradeBlocked` | Requested release transition lacks a supported/approved path |

Stale generation status cannot establish new-spec readiness. A prior applied release
may remain Available while a new release is blocked; both identities stay explicit.
Remote target failures appear in their health/coverage records, not platform liveness.
No report payloads, personal data or credentials enter Kubernetes status/events.

## Deletion and recovery

Deleting the CR removes only verified product-owned resources. Referenced Secrets/
ConfigMaps, external DB/PVCs, retained records, targets and Keycloak/RHBK resources
are preserved. No target API call is necessary for uninstall. Finalizers have bounded
retries and an administrator recovery procedure; remote unavailability cannot trap
deletion. Data retention is an explicit backend policy, not an assumed side effect.

Manual-installation adoption is excluded initially. Recovery/return to manifests
requires explicit ownership handoff, one reconciler and preservation of IDs/history.
Never promise image downgrade can undo a database migration.

## Acceptance cases to implement, not executed results

- Valid desired state converges twice without extra resources; controller restart
  preserves ownership and reports the correct observed generation.
- Reject foreign-namespace reference, second CR in the same namespace, unknown field,
  extra replica, unauthenticated mode, unapproved release or broad permission request.
- Foreign same-name resource is preserved; absent Secret, failed TLS/IdP/DB and bad
  image produce distinct sanitized failure conditions, never a permissive fallback.
- Reference rotation, drift correction and bounded failed rollout preserve records;
  no credential canaries appear in logs/status/events.
- Installation without targets grants no access; onboarding never toggles read-only
  implicitly; remote target failure does not restart a healthy platform.
- Unsupported upgrade is blocked; an accepted upgrade path and uninstall preserve
  external data and all evaluated workloads, with independently recorded evidence.

See the [non-deployable example](examples/keycloak-operations-v1alpha1.yaml).
