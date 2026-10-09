# D1 — Bounded local runners and owned cleanup, 2026-09-18

Continuation and final validation: [19 September runner closure](d1-runner-validation-2026-09-19.md). This earlier ledger preserves the interrupted state and interim results; the follow-up resolves process-group probe uncertainty and records the final suite/runtime evidence.

## Scope and source

Approved follow-up to the [Podman recovery](h1-admin-boundaries-recovered-2026-09-18.md). This slice hardens the two local identity/installation test entry points; it does not change product services, permissions, assessments, UI or cluster adapters. The earlier frozen VM and automatic-cleanup SSH EOF remain historical failures with unknown underlying cause. No automatic VM restart is introduced.

Repository: `feature/0.8.1-client-lifecycle`, HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`. The accumulated tracked/untracked working tree was preserved (272 status entries at entry). No commit, push, rebase, release/tag or real-cluster access. Java **21.0.10** is selected per command through jenv; standalone harness tests use host Node **25.6.1**. No global Java/runtime setting was changed.

Versions explicitly unchanged: backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/package-lock root **0.8.1-dev.0**, report schema **1.1**, Flyway **V1–V10**. This is a test-harness/documentation increment, not a product release or migration. Architecture, operator guide, AGENTS ownership policy, D1/milestone index and continuation context are updated together.

## Implementation

- `lab-process.mjs`: bounded child execution, output capture and loopback readiness; POSIX process groups with TERM/KILL and cleanup confirmation. CLI status preserves failure/interruptions; unknown termination fails closed.
- `identity-lab-runtime.mjs`: explicit local machine connection captured as URI/SSH identity path; UUID/purpose/project ownership; bounded recognized transport retries for reads/cleanup; no automatic repeat of ambiguous Compose startup. Containers are inspected/rechecked and removed only by full IDs, without volume deletion. The owned network must be verified empty and is removed by ID without force, followed by absence checks.
- Shared Bash lifecycle: responsive foreground waits, supervised backend/UI/fixture processes, existing/stale lock preservation, and lock release only after confirmed process/resource cleanup. Completed jobs are not blindly signaled using potentially reused PIDs. Original functional failure or interruption is not changed to success by cleanup.
- Compose labels identify each run; existing names/network are refused even when metrics are disabled. Existence-check errors never imply absence. Images/volumes are never pruned or removed by the helper.

Exact bounds and recovery instructions are in the [operator guide](../../dev/identity-lab/README.md). These are local developer harnesses, not a REST/MCP command-execution capability. No dependency or production deployment changed.

## Validation

Fresh pre-edit baseline and final `JENV_VERSION=21 jenv exec mvn clean verify`: each **1242 passed, 0 failures/errors; 9 opt-in integration tests skipped**, exit **0**. Final build finished at 20:45:06 −03. Backend source was not changed by this slice; the clean rebuild supplies the package used below.

Harness regressions and local runtime scenarios are being finalized. Their final counts, exits, source/test/artifact hashes and independent cleanup inventory will be recorded here before handoff.

Interim evidence is retained: the first combined harness invocation still used four old exported-Bash-function mocks, so **85/89 passed and four failed** without contacting Podman. Replacing those mocks with isolated executable fakes fixed subprocess interception. Subsequent 104- and 113-test runs passed as additional regressions were added. Isolated process tests also needed permitted loopback binding; a restricted run reported EPERM rather than application failure. These interim results are not substituted for the final suite.

## Limits and next step

Passing fake-runtime fault injection is not a real VM crash/restart test. Readiness confirms loopback HTTP 2xx headers, not full response semantics. Process-group control cannot recover from host crashes, SIGKILL of the supervisor or deliberate session escape; uncertain cleanup keeps the lock for manual inspection. It does not lock out an independent human/admin manipulating the same runtime; ownership/ID checks and non-force network removal constrain cleanup, but do not create an atomic remote transaction.

No new manual browser/token-revocation walkthrough, Linux CI execution, image/security scan, RHBK/OpenShift acceptance or reference-agent acceptance is implied. H1/D1 remain open. Next local product work: configured-versus-observed capability/source coverage and remaining metadata/browser trust gates, then the read-only AGT1 prototype. D2 needs the explicitly approved dedicated environment.
