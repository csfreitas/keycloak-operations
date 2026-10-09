# D1 real metrics and authenticated reports — 2026-09-11

Scope: optional local Prometheus collection and authenticated operations reports through REST, MCP and the browser. This extends the [return-path slice](d1-return-path-2026-09-11.md), without changing backend/UI source or declaring H1/D1 complete.

## Implementation and trust boundaries

The [identity lab](../../dev/identity-lab/README.md) adds an opt-in `--metrics` Compose profile. Named `kcops-identity-prometheus` uses cached Prometheus v2.55.1, loopback port 18490, one-hour retention and tmpfs storage. Both Community Keycloak 26.7.1 targets expose management metrics inside the private fixture network; Prometheus scrapes A and B with separate `target_id`/`service` labels. Only A has a configured metrics provider. B stays NONE despite the presence of its samples. The default mode keeps both providers NONE and starts no Prometheus.

The runner selects A's provider through a process-local fixture variable in `platform.properties`. No global Java/environment selection, target permission, production setting or caller-supplied endpoint/query is introduced. Both demo identities retain READ/ASSESS only on their own target; global read-only remains enabled.

The Node verifier now generates reports over the real MCP protocol, decodes its JSON/SSE envelope, and checks target identity, schema, deterministic Markdown, unavailable score/replay, fixture-secret canaries and foreign-target denial. REST additionally checks positive JVM heap samples, null unavailable container memory and a still-PARTIAL report. Existing real signed identity, upstream credential isolation, persisted assessment access, actual target-filtered SSE and negative-token checks remain active. Canary checks are bounded checks, not proof against all possible secret leakage.

## Fresh validation

| Execution | Result and scope |
|---|---|
| Java 21 via jenv, clean verify baseline | 376 backend tests passed, 9 opt-in ITs skipped; build SUCCESS; backend source unchanged in this increment |
| UI regression and production build | 133 tests / 19 files passed; build successful; UI source unchanged |
| Metrics run 1 | 69 checks plus compact-JWT log check passed; exit 0 |
| Metrics run 2, then browser walkthrough | 69 checks plus compact-JWT log check passed again; browser observations below; exit 0 after owned-resource cleanup |
| Default mode regression | 66 checks plus compact-JWT log check passed; both REST/MCP performance sections SKIPPED; exit 0 |
| Script syntax and whitespace | Bash/Node syntax and git diff whitespace checks passed |
| Documentation reconciliation | 121 Markdown documents, 551 local links, 138 requirement definitions and 15 executable milestone specifications checked; no missing links/anchors, duplicate/undefined requirement references or unclosed fences |

The two metrics runs were consecutive clean executions of the same executable fixture/check source. Only operator documentation was subsequently edited while the browser mode remained open. A final comparison with the previous source manifest found changes only in the six lab/configuration/verification files; all 521 current manifest hashes matched disk. This is local Community Keycloak/Prometheus evidence, not an opt-in Maven IT, stress test, production availability result or RHBK/OpenShift validation.

Two earlier exploratory runs failed because the report saw A as metrics-unconfigured: the additional properties file still selected NONE despite the intended Java property override. The second run retained explicit SKIPPED diagnostics. Replacing the competing override with the fixture variable resolved the observed behavior; assertions were not weakened. Both failed runs cleaned their owned resources. The browser tab was initially opened before UI readiness and showed connection refused; reloading after the runner announced readiness succeeded. No application fix is inferred from that startup timing.

## Browser observations

In Brave, `alice-a` signed in at the exact loopback UI origin and returned to A's report deep link. Generate Report at **13:15:25 UTC** showed:

- Report PARTIAL, assessment **Inconclusive — incomplete evidence**, infrastructure NONE and overall health UNKNOWN.
- Performance section COMPLETE; source PROMETHEUS, provider AVAILABLE, target `lab-keycloak-a` and a 15-minute query window.
- JVM heap used **445,133,968 bytes** at that collection; container CPU/memory and HTTP percentile fields null, with NOT_AVAILABLE entries. A requested 15-minute window does not imply 15 minutes of retained lab data.
- Explicit independent collection window, unavailable retained-evidence replay, real deterministic findings and scoped Admin API HEALTHY; this does not establish full environment health.

After sign-out, `bob-b` signed in and Fleet listed only B. Generating B's report at **13:17:57 UTC** showed performance SKIPPED with “Metrics provider is not configured for this target”, no performance measurements, report PARTIAL and assessment inconclusive. B signed out; the login form was visible and the owned browser tab was closed. These are interactive visible-DOM observations, not a committed video/browser automation suite. Download controls were visible but file download/export was not tested in this slice.

## Source, versions and resource hygiene

- HEAD `572cb7b`, branch `feature/0.8.1-client-lifecycle`; existing tracked/untracked changes preserved. Work remains uncommitted. [Source manifest](evidence/d1-metrics-report-source-2026-09-11.json): **521 files**, SHA-256 aggregate **24f5d39655daf638713e01c4aadac4678bec567077e886eaef76d8db97b578c6**, over sorted file hash, two spaces, path and newline. Includes tracked/untracked source/build/fixture inputs, excludes installed dependencies/build outputs and final docs; unsigned local evidence. [Retained check output](evidence/d1-metrics-report-runs-2026-09-11.txt) identifies all three successful runs.
- Backend/application/MCP/OpenAPI remain `0.8.1-SNAPSHOT`; UI/lockfile `0.8.1-dev.0`; report schema 1.1 and Flyway V1–V10 unchanged. No dependency update, commit, push, rebase, tag, release or cluster access.
- Final Podman inventory: **0 containers, 0 volumes**, default `podman` network only; backend/UI/management test ports have no listener. Four reusable images retained: Community Keycloak 26.7.1, PostgreSQL 16, RHBK 26.6 and Prometheus v2.55.1. RHBK was not started. Only identifiable disposable fixtures were removed, including their nonrecoverable temporary database/TSDB state. No persistent lab data volume or global prune was used.
- Baseline logs: `/private/tmp/kcops-metrics-report-baseline.log`, `/private/tmp/kcops-metrics-report-ui-tests.log`, `/private/tmp/kcops-metrics-report-ui-build.log`.
- Successful metric logs: `/private/tmp/kcops-metrics-report-final1.log` and `/private/tmp/kcops-metrics-report-final2.log`; platform logs `/private/tmp/kcops-identity.jGRuxq` and `/private/tmp/kcops-identity.qUzIJ8`. Exploratory logs: `/private/tmp/kcops-metrics-report-run1.log` and `/private/tmp/kcops-metrics-report-diagnostic.log`; default regression: `/private/tmp/kcops-metrics-report-default.log`, platform logs `/private/tmp/kcops-identity.qypkbk`.

## Remaining acceptance and next step

The raw embedded infrastructure inventory still contains legacy negative/boolean defaults under NOT_CONFIGURED, despite the corrected overview and explicit partial report sections. H1 must review/normalize that report projection before claiming all missing-data representations are unambiguous. The performance-bearing report also lists missing performance-rule evidence; this slice establishes actual samples and honest inconclusive output, not complete end-to-end metric-to-rule coverage. Missing-provider is tested; runtime provider failure/authorization/rate-limit behavior is not newly covered here.

Next local work: address remaining report trust/projection gaps and validate installation discovery/review/confirmation with real local identity against an explicitly configured synthetic cluster API. Remaining browser negatives/late-response behavior, dependency review, reference-agent prototype and operator acceptance keep H1/D1 open. No OpenShift cluster is required for these steps; real RHBK/OpenShift remains D2 with an approved dedicated environment.
