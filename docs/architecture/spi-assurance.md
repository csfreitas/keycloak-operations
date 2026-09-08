# Custom SPI assurance

Status: **PLANNED — no custom-code execution endpoint is introduced**.

## Goal and capabilities

Assess/test authorized Keycloak/RHBK extensions reproducibly without arbitrary code execution in the operations platform or managed environment. Follow product-specific extension documentation and test actual releases, rather than assuming binary compatibility. [Keycloak Server Developer Guide](https://www.keycloak.org/docs/latest/server_development/index.html).

1. Inventory: provider metadata, owner-supplied source/artifact reference, digest/build/runtime version and sanitized configuration. Metadata does not prove a JAR's contents or safety.
2. Assessment: source/artifact analysis, supported versus internal APIs, dependencies/SBOM, secret/log handling, lifecycle/thread-safety/resource behavior and relevant tests. Distinguish automation, human review and unknowns; never claim review of unavailable code.
3. Execution: approved suites on a dedicated disposable compatible runtime, producing functional/failure/performance/security evidence. No automatic deployment to customer targets.

## Isolation boundary

Submit approved artifact digest and registered test-suite ID, not raw model-supplied shell/Java/JavaScript. Resolve artifacts/credentials internally. ASSESS, EXECUTE and PROMOTE are different permissions.

Build and run outside the platform in an isolated worker/VM/namespace appropriate to code trust. Containers alone do not guarantee hostile-code isolation. No host mounts/container socket/kubeconfig/production credentials; no service-account token automount; non-root/restricted capabilities/syscalls; read-only root when possible; CPU/memory/disk/time/process budgets; egress denied except explicit test dependencies. Build plugins also execute code and need this boundary.

Persist the attempt before execution. Support cancellation/deadlines/crash recovery, bounded sanitized logs and verified cleanup. Do not reuse contaminated workspaces. Names/labels identify project/artifact/run; test volumes are transient, reusable caches explicitly approved and kept free of secrets/trust-crossing executable output.

A trusted supervisor outside the code under test owns pass/fail and records observations. Artifact stdout, files and self-reported results are untrusted evidence, never self-attestation. Pin runner/image/suite/scanner revisions and vulnerability-database snapshot/freshness; identify stale scanner evidence explicitly.

## Approval and promotion

Require trusted identity, artifact provenance, suite allowlist, environment policy and human approval for untrusted execution. The model cannot approve itself. Promotion uses existing reviewed image/GitOps pipelines, canary/rollback tests and explicit environment authorization, not a generic MCP deploy tool.

## Evidence and acceptance

Artifact/source digest, dependencies/SBOM, runtime image, suite revision, fixture, start/end, result, coverage and sanitization are recorded. Tests include prohibited network access, attempted escape, infinite loop, exhaustion, secret canary, malicious build plugin, compatibility break and cleanup after failure. Passing a suite only proves those tested behaviors.

This is P3, not required for January. Before implementation, obtain authorized sample code, approve threat model/execution environment and define category-specific tests. Customer/production code is not implicitly authorized for execution.
