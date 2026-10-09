# D1 report trust correction — 2026-09-11

Scope: correct report-only unknown infrastructure values and verify why live metrics can coexist with missing assessment evidence. Builds on the [previous metrics ledger](d1-metrics-report-2026-09-11.md). No OpenShift access, permission expansion or production policy selection.

## Findings and changes

1. Raw inventory records used negative counts, false presence flags and empty collections for some unavailable inputs. The report copied those records without distinguishing unknown observations. `ReportInventoryProjection` now copies and normalizes the report summary before sanitization: unconfigured/opaque failures produce null observation sections; installation/resource warnings suppress dependent observations while preserving independently collected information. Valid zeros and observed absence survive. Invalid counts and missing pod zones do not become measurements. Partial networking keeps associated resources but withholds aggregate presence/TLS/host conclusions and marks collection incomplete.
2. The metrics-to-rule path was not broken by the observed heap case. `MetricsEvidenceCollector` emits numeric heap utilization independently, but emits `metrics.jvm.heapPressure` only when the optional threshold is configured. The previous fixture had no such threshold. A new regression covers absent, exceeded and not-exceeded thresholds; the lab now explicitly uses 85 percent solely as demonstration policy. Missing histograms, other unset SLO thresholds and absent infrastructure still legitimately prevent other rules from evaluating. No default application threshold or collector logic was changed.

Report JSON and deterministic Markdown share the projection, including the compact MCP report's Markdown. Historical snapshots, inventory APIs, rule evidence, persisted hashes and migrations are unchanged. A snapshot hash remains a reference to original stored data, not a checksum for its normalized report view. Dynamic nested report sections/counts can now be null; consumers must handle them. This is not complete retained-evidence replay or certification of every provider/failure state.

## Validation

| Check | Result |
|---|---|
| Fresh Java 21/jenv baseline, clean verify | 376 backend tests passed; 9 opt-in ITs skipped; build SUCCESS |
| Interim focused regression | 27 tests passed before two additional projection cases were added |
| Final clean verify | 392 backend tests passed; 9 opt-in ITs skipped; build SUCCESS |
| UI tests and production build | 133 tests / 19 files passed; build successful; UI source unchanged |
| Real disposable metrics/report run | 75 checks plus compact-JWT log check passed; exit 0 after cleanup |
| Default mode without Prometheus | 70 checks plus compact-JWT log check passed; exit 0 after cleanup |
| Documentation review | 122 Markdown documents, 564 local links, 138 requirements and 15 executable milestone specifications; no broken links/anchors, undefined requirement references or unclosed fences |

The 16 new test cases comprise 14 projection cases, one report JSON/Markdown integration case and one collector policy regression. They cover no infrastructure, missing binding, denial, observed zero/false, per-resource isolation, missing zones, partial networking, missing workload template, opaque legacy warnings, invalid counts, original-snapshot preservation and explicit heap policy. Synthetic inventory tests are not real cluster acceptance. Existing sensitive-data and authorization regressions also passed in the full suite.

The [retained local check output](evidence/d1-report-trust-runs-2026-09-11.txt) records actual signed identity, target-filtered SSE, REST/MCP reports and negatives. A's performance remained COMPLETE with positive JVM samples; its heap-pressure key was no longer missing and at least eight rules evaluated in the performance profile. Other gaps kept the assessment inconclusive. B's performance remained SKIPPED, with no cross-target telemetry. Both reports exposed null unknown infrastructure in structured REST JSON and MCP Markdown. Each mode ran once on this corrective slice; the previous ledger's consecutive runs remain historical. No fresh browser walkthrough or RHBK/OpenShift/opt-in Maven IT was run here.

## Source and hygiene

HEAD remains `572cb7b` on `feature/0.8.1-client-lifecycle`; all existing tracked/untracked work is preserved and uncommitted. [Source manifest](evidence/d1-report-trust-source-2026-09-11.json): **523 files**, aggregate SHA-256 **01db413f6806803aab3e76a5711b692efd80dffb0be3a84099c96fb7d9f06cef** over sorted file hash, two spaces, path and newline. Includes tracked/untracked source/build/fixture inputs, excludes installed dependencies/build outputs and final docs; unsigned local evidence. Versions remain backend/application/MCP/OpenAPI `0.8.1-SNAPSHOT`, UI/lockfile `0.8.1-dev.0`, report schema 1.1 and Flyway V1–V10. No dependencies, global Java settings, production defaults, commit, push, rebase, release or deployment were changed.

Final Podman inventory: **0 containers, 0 volumes**, default `podman` network only; no backend/management test listeners. Four reusable cached images preserved: Community Keycloak26.7.1, PostgreSQL16, RHBK26.6 and Prometheusv2.55.1. RHBK was not started. Only owned transient containers/network and nonrecoverable disposable database/TSDB contents were removed; no global prune. Metrics platform logs: `/private/tmp/kcops-identity.EPyaoQ`; default platform logs: `/private/tmp/kcops-identity.My3DNj`.

Logs: `/private/tmp/kcops-report-trust-baseline.log`, `/private/tmp/kcops-report-trust-focused.log`, `/private/tmp/kcops-report-trust-verify.log`, `/private/tmp/kcops-report-trust-ui-tests.log`, `/private/tmp/kcops-report-trust-ui-build.log`, `/private/tmp/kcops-report-trust-metrics.log`, `/private/tmp/kcops-report-trust-default.log`.

## Remaining work

This closes the previously observed report projection defect and explains/tests the heap-policy gap, not every possible metric-to-rule mapping. Remaining threshold policy choices must come from the operator; unavailable sources remain explicit. Inventory APIs/retained snapshots still use their historical model, and broader collector/engine errors, bounds, dependency review, browser negatives/revocation and reference-agent acceptance remain H1/D1 work. Next local delivery: installation discovery/review/confirmation using real identity and an explicitly configured synthetic cluster API, with separate setup/reader roles and unchanged global defaults. Real RHBK/OpenShift still requires the dedicated approved D2 environment.
