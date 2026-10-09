# PORT1 — Scoped Podman, Compose and Docker inventory

Status: **PLANNED**. Owner: collector developer + host/security reviewer. No new delivery date; product track, not a prerequisite for D2.

## Objective and dependencies

Assess actual container hosting without requiring Kubernetes. Depends on approved connection/installation contracts (H1/D1 and ONB1 for managed registration); explicitly configured connections may support an initial fixture. See [portable discovery](../architecture/portable-environment-discovery.md).

Roadmap: portable discovery, INF-01, CFG-02. Requirements: FR-DISC-001–004, FR-INV-001–003, SEC-INFRA-004/005, NFR-ISO-001, NFR-BOUND-001, COMPAT-005/006.

## Implementation slices

The [hub installation Operator](../architecture/operator-managed-platform.md) does
not install host collectors or expose runtime sockets. Each collector/transport has
separate approval, scoped identity and lifecycle acceptance.

1. Threat model and bounded host-local collector protocol. Keep privileged engine sockets outside the MCP/UI process; no raw inspect/exec or arbitrary file/command API.
2. Podman fixtures first: explicit engine/host/rootless identity, approved project/labels/container IDs, sanitized runtime/topology/resource observations.
3. Compose project/service relationships with provider/version/label availability; distinguish desired compose configuration from live resources.
4. Separate Docker implementation/compatibility tests; map capabilities and unsupported fields without synthetic Kubernetes objects.

## Exit criteria

- [ ] Two hosts/projects with identical names remain isolated; container recreation changes identity and requires reconciliation.
- [ ] Running/exited/denied/unreachable/ambiguous states are distinguishable; missing collection is not empty/healthy inventory.
- [ ] Secrets, unrestricted env/command line, mounts and raw config never enter exports; malicious metadata cannot control the collector.
- [ ] Bounds/timeouts and rootless permissions are tested; exact Podman/Compose and Docker versions recorded separately.
- [ ] Fixtures clean up only owned transient resources even on failure; reusable caches/volumes have a documented purpose.

No inventory-driven cleanup, socket read-only claims, automatic installation or compatibility extrapolation between engines. PORT2 covers hosts/standalone separately.
