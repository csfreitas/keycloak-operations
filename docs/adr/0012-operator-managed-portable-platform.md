# ADR 0012 — Operator-managed installation, portable operations

- Status: Accepted product direction; installation contract is a draft, implementation planned in OP1.
- Date: 2026-10-09

## Context

The product should install and configure naturally in OpenShift while assessing
Keycloak/RHBK in the same cluster, other OpenShift/Kubernetes clusters and external
VM/container/standalone deployments. The current modular backend already separates
Keycloak, infrastructure and metrics connections by target. Existing deployment
files are templates, not an implemented Operator or real-cluster acceptance.

Installation authority and operational assessment authority are different. Giving
the MCP process the installer's privileges, or treating placement in a cluster as
permission to discover every workload, would expand the trust boundary unnecessarily.
The current single infrastructure binding per target, in-process MCP/SSE state and
stub scheduler also preclude claims of multi-site modelling or horizontal HA.

## Decision

1. Add a separate **Keycloak Operations Operator** to reconcile only the product's
   installation and lifecycle. Start with one namespaced installation resource and
   one backend replica. Keep a supported non-Operator installation path; the domain
   and assessment engine must not depend on OpenShift APIs.
2. Retain the modular **Operations hub**: console, REST/MCP, shared authorization,
   approved connection/target registry, evidence, deterministic rules and reporting.
   The console works without AI; neither a model nor the install controller decides
   findings, grants access or supplies operational endpoints.
3. Prefer direct, explicit, least-privilege API connections. Add a bounded collector
   only where network/trust constraints or host/container evidence justify it.
   Connected collectors initiate authenticated outbound communication when needed;
   truly disconnected sites require an explicitly imported offline evidence bundle.
   Neither transport is implemented by this decision.
4. Keep installation desired state in the installation CR (GitOps when adopted),
   operational records in the registry and observations/history in persistence.
   Each object/field has one owner. A later declarative registry adapter must use
   the same versioned administrative policy/API as the UI, not direct database writes.
5. Do not adopt or reconcile observed Keycloak/RHBK workloads, CRs, databases or
   Operators. Their existing owners remain authoritative. No installation, discovery
   or assessment implies remediation, provisioning or target access grants.
6. Keep platform-user identity separate from target credentials. Separate installer,
   backend/UI and collector service accounts; references do not permit arbitrary
   cross-namespace Secret access. Use separate hubs for incompatible trust domains.

## Alternatives and consequences

- **Manifests only:** smallest immediate deployment and valid for D2; retained as a
  baseline, but does not automate lifecycle/configuration reconciliation.
- **Everything in one Operator process:** less packaging, but couples privileged
  reconciliation, sessions, remote latency and assessments; rejected.
- **A complete hub in every cluster:** useful for isolated trust domains, but not
  mandatory; duplicates state/operations and does not by itself cover external VMs.
- **Mandatory collector everywhere:** reduces direct connectivity needs but adds
  deployment, identity, upgrade and buffering costs without a demonstrated need;
  optional instead. Federation and a distributed service mesh are not prerequisites.

This adds a controller, versioned installation API, packaging and lifecycle tests.
It does not remove ONB1 registration work, D2 real RHBK/OpenShift acceptance,
PORT1/PORT2 collectors or P4 availability/recovery gates. OpenShift-native packaging
does not establish Red Hat support/certification. Product, Operator, installation
API and collector/evidence revisions are separate compatibility axes.

## Adoption, recovery and reconsideration

First freeze the contract and validate the existing deployment baseline in an
approved cluster. Do not auto-adopt a running manual installation or seed unrelated
lab targets. Migration requires an explicit ownership/configuration/database plan,
with stopped competing writers and preservation of target IDs, bindings and history.
Deletion affects only verified product-owned resources and retains referenced data.

Operator and operand upgrades are separate: a controller upgrade cannot silently
select a new application version. Application changes require a verified release
and a safe migration path. Image rollback is not database rollback; unsupported
paths must stop with a condition instead of retrying destructive work.

Returning to manifests requires explicit ownership handoff and only one active
reconciler. Revisit this decision if measured scale, regulation, offline requirements
or validated HA needs justify additional processes or regional hubs, not merely
because there are many targets.

See [architecture](../architecture/operator-managed-platform.md),
[draft installation contract](../architecture/operator-installation-contract.md)
and [OP1 acceptance](../milestones/op1-operator-installation.md).

Official basis: [Kubernetes Operator pattern](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/),
[RBAC good practices](https://kubernetes.io/docs/concepts/security/rbac-good-practices/),
and [RHBK 26.4 Operator guide](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/26.4/html-single/operator_guide/index).
These describe patterns and that product version, not tested compatibility of Operations.
