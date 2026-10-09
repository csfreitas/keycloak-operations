# D1 — Runner validation closure, 2026-09-19

## Scope and source

Continuation of [18 September runner hardening](d1-runner-hardening-2026-09-18.md), including the milestone/context updates previously blocked by execution quota. HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch `feature/0.8.1-client-lifecycle`; accumulated tracked/untracked changes are preserved. This increment changes the local process supervisor and two regression cases. The already implemented full-ID fixture SQL path is now exercised against the disposable local installation lab.

Backend/application/MCP/OpenAPI remain **0.8.1-SNAPSHOT**, UI and root package lock **0.8.1-dev.0**, report **1.1**, Flyway **V1–V10**. No product dependency, permission, schema, endpoint or UI change. Java 21 is selected per command through jenv. No commit/push/rebase/release or real-cluster access.

## Root cause and correction

The previous supervisor permanently set `cleanupFailed` on any non-ESRCH group probe or signal error. A local reproduction observed **EPERM followed by ESRCH** while reaping terminated process groups: 11 of 12 trials incorrectly retained cleanup exit 125 after absence was confirmed. This explains the reproduced false cleanup failure; it does not establish the cause of the earlier Podman VM freeze or SSH EOF.

The supervisor now treats a failed probe as unknown and continues within the existing bounded deadline. Successful independent group-absence observation resolves that uncertainty. Signal acknowledgement never substitutes for absence verification. A still-present or unobservable group at the final deadline still fails with exit 125 and retains the runner lock; original command failure/timeout/interruption codes remain intact. The earlier one-second verification window measured from the actual KILL callback is retained.

Two deterministic regressions cover a transient probe denial and a transient signal denial followed by verified absence. The existing persistent-denial case continues to require exit 125. A real local reproduction after the correction passed **12/12 trials**, retaining expected timeout exit 124 with `cleanupFailed=false`; nine trials still observed the transient EPERM before ESRCH.

## Recorded validation

- Fresh pre-edit Java 21/jenv `mvn clean verify`: **1242 passed; 9 opt-in ITs skipped**, exit 0. Backend sources are unchanged by this increment; this build supplies the lab package.
- Loopback-permitted pre-edit harness: **136/136 passed**, clearing the earlier sandbox listen-EPERM failures. Those permission failures were distinct from the process-group probe issue.
- Final harness: **138/138 passed**, exit 0. This includes process/readiness, runtime ownership/database guards and Bash lifecycle checks.
- Final installation lab: **83 checks passed**, JWT scan passed, owned cleanup verified, runner exit 0. Uses real local OIDC/Java/PostgreSQL and a synthetic loopback cluster API; it exercises the inspected full database ID and pinned Podman URI.
- Final default identity lab: **70 checks passed**, JWT scan passed, owned cleanup verified, runner exit 0.
- Final metrics lab: **75 checks passed**, JWT scan passed, owned cleanup verified, runner exit 0.
- Independent final inventory at **14:49:05 −03**: **zero containers and volumes**, default Podman network only, four reusable image IDs unchanged, all eleven fixture ports bindable and shared lock absent. Only disposable test resources/data were removed; tmpfs contents are not recoverable, while images/imports/diagnostic logs remain available.

The [evidence manifest](evidence/d1-runner-validation-2026-09-19.json) records **575 source inputs**, aggregate `8dc10a89784ca84ed3454054c9afbb506631aca014be46981127f732a4e658fe`, 115 Maven report hashes, seven fresh log hashes/excerpts, three package hashes and the final inventory. Compared with the last 571-input source manifest, seven harness/guide files changed and four helper/test files were added; none were removed. Raw logs use `/private/tmp/kcops-runner-resume-*-20260919.log` and may eventually expire; hashes and excerpts are unsigned local evidence, not release attestations. The 18 September installation nonzero result and earlier failed signal runs remain historical evidence.

## Limits and next step

Final bookkeeping checks: **701 referenced hashes matched**, including all 575 source inputs. Offline documentation validation covered **133 Markdown documents, 775 local links and 15 milestone specifications**, with zero errors/warnings; `git diff --check` passed. These checks verify consistency and references, not milestone acceptance or external URLs.

This closes only the tested local runner reliability slice. H1/D1 acceptance still requires their remaining trust, browser/revocation and reference-agent gates. POSIX process groups do not cover deliberate session escape, host crashes or arbitrary external runtime mutations. No new browser walkthrough, Linux CI execution, container vulnerability scan or real RHBK/OpenShift acceptance is implied.

Next local product slice: configured-versus-observed capability/source coverage, followed by remaining metadata/browser trust work and AGT1. D2 still requires the dedicated approved environment. All historical results retain their original dates and limitations.
