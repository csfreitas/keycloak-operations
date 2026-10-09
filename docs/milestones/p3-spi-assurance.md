# P3 — Isolated SPI assessment and test execution

Status: **PLANNED**. Indicative planning: Q2 2027. Owner: extension/runtime developer + security reviewer + artifact owner.

## Objective and dependencies

Review and test authorized custom extensions without executing untrusted code in the operations platform or customer targets. Depends on approved artifact access, threat model, isolated execution environment and D3 provenance; promotion additionally depends on P2 governance. Design: [SPI assurance](../architecture/spi-assurance.md).

Roadmap: SPI-01/02/03. Requirements: FR-SPI-001/002, FR-EVID-001, SEC-AI-001, SEC-INFRA-001, SEC-CRED-002–004, NFR-BOUND-001.

## Ordered sub-slices

1. SPI-01: owner/source/artifact digest, build/dependencies/SBOM, supported versus internal APIs and sanitized configuration; review unavailable code as NOT VERIFIED.
2. SPI-02: approved digest + registered suite ID, durable attempt, trusted external supervisor, CPU/memory/disk/time/process limits, cancellation and recovery. Build plugins share the untrusted-code boundary.
3. SPI-03 only later: reviewed image/GitOps promotion, human approval, canary/rollback tests and environment-specific authorization.

## Exit criteria

- [ ] Tests run outside the platform/customer workload; no production credentials, host mounts, engine sockets, ambient kubeconfig or token automount.
- [ ] Prohibited egress, malicious build plugin, infinite loop, exhaustion, secret canary, compatibility failure and crash cleanup cases pass the supervisor's checks.
- [ ] Image/suite/scanner/database revisions, artifact digest, observations and sanitization are retained; artifact stdout is untrusted, not self-attestation.
- [ ] Disposable workspace/resources are removed after failure; reusable cache policy prevents secret or executable-output trust crossing.

No arbitrary code upload/exec/deploy tool, customer JAR execution without authorization, hostile-code safety claim from containers alone, or universal compatibility certification. Not a January requirement.
