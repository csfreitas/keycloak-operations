# OP1 — Operator-managed Keycloak Operations installation

Status: **PLANNED — design/contract only**. No controller, CRD, bundle or live
Operator acceptance. Owner: development + platform/security reviewer + independent
operator. No new deadline; not a prerequisite for the existing presentation.

## Objective and dependencies

Install/configure/reconcile the Operations hub on OpenShift while retaining portable
application assessment. [ADR 0012](../adr/0012-operator-managed-portable-platform.md),
[architecture](../architecture/operator-managed-platform.md) and
[draft 0.1 contract](../architecture/operator-installation-contract.md) define scope.

Depends on H1/D1 contracts and deployment-baseline consolidation. D2 can validate
the reviewed manifests first and does not depend on OP1. Acceptance requires an
explicitly approved OpenShift/version/namespace; no ambient cluster. ONB1 minimum
registration/ownership/write-gate acceptance is required for claiming an onboarded
fleet workflow, not for an empty installation. Full registry CRDs are not an OP1
prerequisite. PORT1/PORT2 collectors and P4 HA/production readiness remain separate.
ONB1 integration also needs auditable first-administrator bootstrap and a separately
governed grant channel; neither empty installation nor registration grants READ/ASSESS.

Roadmap: OPR-01. Requirements: FR-OPERATOR-001/002, SEC-OPERATOR-001,
FR-ONBOARD-001, FR-OPS-001, NFR-ISO-001, SEC-INFRA-004/005, COMPAT-005/006.

## Implementation slices

1. Freeze final API group/schema and approved release metadata; turn draft invariants
   into schema/controller negative tests. Consolidate one-replica baseline, runtime UI
   OIDC configuration, supported TLS path and empty-registry defaults. Do not expose
   arbitrary image/property/pod-template escape hatches.
2. Separate namespaced controller; idempotent owned-resource reconciliation,
   restricted references, independent accounts, conditions and no automatic adoption.
3. Test drift, rotation, restart/rollout interruption, dependency failures and safe
   uninstall. Block unaccepted application/database transitions; do not infer migration
   coordination from Flyway startup or make the installer manage external databases.
4. Validate the exact installation path on the approved cluster and independently
   reproduce it. Add bundle/catalogue packaging only with its own install/upgrade
   evidence and version policy; do not describe generated YAML as accepted distribution.

## Exit criteria

- [ ] Same spec reconciles repeatedly without duplicated resources or target writes;
  restart and conflicting/foreign resource identity are tested.
- [ ] First install refuses an unadopted populated/incompatible database without
  modifying it; restart of the same verified installation preserves records/history.
- [ ] Final schema rejects unknown fields, invalid release, cross-namespace references,
  second installation, replicas above one and authentication/read-only bypasses.
- [ ] Effective RBAC separates installer/backend/UI; denied out-of-scope reads/writes
  and no automatic target grants are demonstrated.
- [ ] UI OIDC/PKCE, backend audience validation, TLS and source-free initial registry
  work on exact recorded images; invalid IdP/DB/certificate fail closed.
- [ ] Status reflects generation, applied release and bounded failures; it does not
  equate platform availability with healthy or fully observed targets.
- [ ] Secret/reference rotation and workload drift produce documented outcomes;
  credential canaries are absent from status, logs, events and reports.
- [ ] Uninstall removes only verified owned resources, retaining referenced DB/PVCs,
  Secrets, records and every observed Keycloak; failure/recovery procedure is tested.
- [ ] Unsupported upgrade/migration is blocked. Every transition advertised as supported
  has exact image/schema, backup/restore and approval evidence; no false downgrade claim.
- [ ] An independent operator reproduces the guide; source, versions, digests, RBAC,
  tests/skips, interruption limits and final resource inventory are retained.
- [ ] Any claimed bundle/catalogue installation and upgrade uses that exact accepted
  path; unimplemented packaging remains explicitly unavailable.

## Exclusions and handoff

No target provisioning/remediation, Keycloak Operator replacement, implicit cluster
discovery, database provisioning, host collector, remote shell/socket, model rollout,
horizontal HA or universal support claim. Single-replica rollout may interrupt clients.
No existing H1/D1/D2/AGT gates are closed by this design.

ONB1 owns registration and future CR/UI ownership; its global read-only/BIND semantics
must be resolved explicitly, not bypassed by the controller. D2 owns RHBK/platform
collection acceptance. Expanded multi-cluster/VM matrices require separately approved
environments and declared coverage; P4 governs supported release scope and recovery.
