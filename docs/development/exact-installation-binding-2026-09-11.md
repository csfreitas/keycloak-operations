# Exact installation binding — 2026-09-11

Historical ledger for the binding slice. The subsequent [installation networking slice](installation-networking-2026-09-11.md) restores bounded Service/Ingress/Route configuration association; the limitations below describe the original binding delivery.

## Delivered scope

The existing target now optionally carries a Kubernetes/OpenShift installation root: API version, kind, name and immutable resource UID, within its explicitly configured cluster identity, namespace and credential reference. Supported roots are `apps/v1` Deployment/StatefulSet and a versioned `k8s.keycloak.org` Keycloak CR. Configuration, domain validation, additive V9 persistence and the read-only target API carry this identity. This is a backend safety slice, **not** a candidate-discovery wizard or new connection CRUD.

Inventory reads the exact root by name and checks namespace, API/kind and UID. Missing binding returns `BINDING_REQUIRED` before opening a cluster client. Missing root, denied access, identity mismatch and ambiguous owned workloads produce warnings without falling back to another resource. A same-name replacement requires explicit administrator reconfirmation. Deployment pods are associated through controller ownership `Deployment → ReplicaSet → Pod`; StatefulSet pods use direct controller ownership. A Keycloak CR must have exactly one verified controller-owned Deployment/StatefulSet to supply workload health. CR desired instances alone do not establish ready replicas.

The binding returned in `/api/v1/targets` and `/api/v1/targets/{targetId}` is **configured intent**, not live verification. Unbound targets return `installation: {}`. Inventory `keycloak.apiVersion/kind/uid` and `keycloak.workload.*` evidence identify the observed resource; for an Operator root this can be its owned workload, not the CR itself. Existing target authorization/redaction applies; no credentials are added to responses. There is no write endpoint or model-supplied binding mutation.

## Migration and configuration

V9 adds four nullable columns and an all-or-none constraint; it never invents bindings for existing rows. Existing configuration loads unbound. Keycloak API operations remain independent; cluster inventory now requires confirmation. The existing bootstrap synchronizes configured targets into the database, so change the approved server configuration rather than editing only a database row that bootstrap may overwrite.

Example to add to an **existing administrator-approved target**, with actual values obtained by the administrator from an authorized source:

```properties
mcp.targets.example.infrastructure.type=KUBERNETES
mcp.targets.example.infrastructure.cluster-id=approved-lab-connection
mcp.targets.example.infrastructure.namespace=iam
mcp.targets.example.infrastructure.credential-ref=approved-cluster-credentials
mcp.targets.example.infrastructure.installation.api-version=apps/v1
mcp.targets.example.infrastructure.installation.kind=StatefulSet
mcp.targets.example.infrastructure.installation.name=rhbk
mcp.targets.example.infrastructure.installation.uid=REPLACE-WITH-OBSERVED-UID
```

The example UID is a placeholder, never a default. Supply all four installation properties together. For an Operator CR supply its actual served API version, kind `Keycloak`, name and UID. This slice does not automatically enumerate served CRD versions. The OpenShift ConfigMap includes commented binding properties so a template cannot silently select an installation.

`cluster-id` is a configured connection identifier, not an observed cryptographic cluster UID. TLS, endpoint/credential controls from the [explicit connection slice](explicit-infrastructure-connections-2026-09-11.md) remain required. Changing an approved endpoint/connection is an administrator trust decision. Runtime resources are never registered automatically and no permissions are granted by binding them.

## Evidence integrity and remaining coverage

- HPA association checks API version, kind and name. PDB `matchLabels` requires the complete selector to match workload template labels, not a single shared label or similar name. Multiple matching HPAs/PDBs are reported as ambiguous instead of first-match selection. Expression-based PDB association remains unsupported.
- Missing/denied workload or pod collection suppresses dependent zero/absence assessment evidence. PDB/HPA failures suppress corresponding policy evidence. Warnings must be shown with inventory; an empty pod list with a collection warning does not prove absence.
- Route/Ingress collection is intentionally unavailable (`networking: null`, `NOT_SUPPORTED` warning) until explicit Service/backend correlation is implemented. It no longer borrows another installation's route by name. This is a documented reduction of automatic coverage for safety; assessments must not infer missing ingress from it.
- Node and cluster observations remain cluster-wide, not installation-exclusive. Ownership filtering prevents unrelated pods from appearing in the returned inventory but namespace list permissions still allow the collector to receive other resources. Namespace/resource-count limits and richer snapshot provenance remain follow-up work.
- Candidate enumeration/confirmation UI, audited reconciliation workflow, normalized connection entities, multi-installation targets, history, Docker/Podman/Compose/VM/standalone infrastructure collectors and real Operator/OpenShift compatibility validation are **not delivered**. Primary-container selection inside an approved workload remains the existing heuristic; a future container-level binding should remove that ambiguity.

Ownership semantics: [Kubernetes owners and dependents](https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/), [ReplicaSet ownership](https://kubernetes.io/docs/concepts/workloads/controllers/replicaset/).

## Validation ledger

- Starting HEAD `572cb7b`, existing uncommitted work preserved. No commit, push, rebase, external deployment or actual cluster access.
- Baseline: Java 21 selected per command through jenv, `mvn clean verify`, **303 passed**, 0 failures/errors, **9 opt-in ITs skipped**, SUCCESS at 2026-09-11 01:10:47 -03:00. Log: `/private/tmp/kcops-installation-binding-baseline.log`.
- Local tests cover same-namespace identical-label isolation, Deployment/ReplicaSet/Pod and StatefulSet ownership, same-name UID replacement, missing/denied roots, unavailable pods, Operator CR ambiguity and missing owned workload, policy association, configuration validation and database round-trip/clearing.
- Final `mvn clean verify`: **321 passed**, 0 failures/errors, **9 opt-in ITs skipped**, SUCCESS at 2026-09-11 01:24:46 -03:00 (54.079 s), Java 21 via jenv. Log: `/private/tmp/kcops-installation-binding-verified.log`. This slice adds 18 tests. V1–V9 migrations applied successfully to fresh transient databases; binding save/reload/clear passed. An upgrade of an existing populated customer database was **not** tested.
- `git diff --check` passed. Final Podman inventory: **0 containers, 0 volumes**, four reusable cached images preserved. No global prune or manual removal of user resources.

Kubernetes APIs are simulated with Fabric8 MockServer; PostgreSQL is transient local test storage. No real cluster, kubeconfig, RHBK environment or host/container infrastructure discovery was tested by this slice. No UI changes were made for binding, and previous UI/identity-lab results are historical rather than reruns here.

Next: review this compatibility change, implement installation-scoped Service/Route/Ingress correlation, then candidate confirmation with dedicated permissions and reconciliation/audit. Preserve the portable discovery roadmap and separate real RHBK/OpenShift gate.
