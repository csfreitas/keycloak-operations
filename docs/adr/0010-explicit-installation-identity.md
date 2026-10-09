# ADR 0010 — Explicit connection and installation identity

- Status: Accepted product direction; current cluster binding/confirmation implemented locally, full portable onboarding remains planned.
- Date: 2026-09-11

## Context

A logical RHBK/Keycloak environment can run on Kubernetes/OpenShift, containers, VMs or standalone services. An ambient cluster context, first matching workload, shared Service selector or reused resource name can associate another installation's data with the target.

## Decision

Keep approved connection, logical target, installation binding and observed inventory separate. Require explicit credentials/scope and exact root identity; never fall back to ambient kubeconfig/socket/SSH or automatically select the first match. Current cluster roots use API/kind/name/UID. Follow ownership and unambiguous backend associations; missing/denied/ambiguous collection remains a gap.

Existing-target confirmation uses expiring server-retained candidates bound to actor/target/context/revision, READ/DISCOVER/BIND and the global read-only guard. Revalidate live UID and persist binding/run/success audit together. This is metadata configuration, not a cluster mutation, human-presence proof or atomic remote snapshot.

## Consequences

- Current V9/V10 does not create new connections/targets or implement host/container collectors.
- ONB1 extends registration/reconciliation; PORT1/PORT2 add collectors with explicit host/runtime identities and bounded allowlisted observations.
- Socket access is privileged even when mounted read-only; no raw engine/command interface is exposed to the model.
- Managed binding precedence and invalidation must remain documented; old ambiguous targets stay unbound, not silently migrated by guessing.
- [Portable architecture](../architecture/portable-environment-discovery.md), [milestones](../milestones/README.md) and [local ledger](../development/installation-onboarding-2026-09-11.md) define actual acceptance and limits.
