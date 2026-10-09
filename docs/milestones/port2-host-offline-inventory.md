# PORT2 — Host/standalone inventory and offline evidence

Status: **PLANNED**. Owner: collector developer + host/security reviewer. No date assigned. Depends on approved portable contracts and D3E for offline replay/import.

## Objective

Support VM/physical-host and standalone JVM/service deployments using a bounded installed collector or sanitized offline bundle, without requiring OpenShift or scanning the user's machine. Reuse PORT1 schema concepts where appropriate, not a container dependency for standalone hosts.

Roadmap: portable discovery, INF-01, DOC-03. Requirements: FR-DISC-001–004, FR-INV-001–003, FR-EVID-001, SEC-INFRA-001/004/005, SEC-CRED-002/003, NFR-BOUND-001.

## Deliverables

The [portable hub design](../architecture/operator-managed-platform.md) keeps a
connected collector and an offline bundle distinct: outbound transport needs network
access; truly disconnected sites require authorized import. Neither is delivered by
installing the hub Operator, and collector installation is independently approved.

- First explicit Linux/systemd + standalone JVM fixture; host identity plus unit/process start identity, runtime versions/resources and supported management observations.
- Independent hosting/runtime/deployment fields; unknown fields/capabilities remain unknown. Windows/non-systemd need separately scoped later validation.
- Offline bundle schema, source/collection time, digest and declared origin/trust level; authorized bounded import rejects malformed, oversized or mismatched-target data.
- Capability-based rule applicability and the same IAM results for identical Keycloak evidence regardless of host type.

## Exit criteria

- [ ] PID reuse/service replacement, inaccessible source, stopped process and old bundle cannot be mistaken for current healthy state.
- [ ] No default SSH credentials, root requirement, filesystem/process sweep or arbitrary command execution.
- [ ] Canaries and sensitive file/command-line material absent; import cannot cause outbound requests, code execution or path traversal.
- [ ] Offline rendering/replay performs no live probe; timestamp and trust limitations remain visible. Hash alone is not authenticity.
- [ ] Real authorized VM and standalone fixture versions, least privileges, tests and exact cleanup recorded.

No auto-provisioning, universal OS support, customer collection or forced host-agent installation. Broad log/trace backends remain separately governed future integrations.
