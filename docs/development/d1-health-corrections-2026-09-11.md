# D1 health and missing-data corrections — 2026-09-11

Corrective scope: the Admin API health result and infrastructure counts in target overview. This supersedes the first two open findings in the earlier [browser ledger](d1-browser-workflow-2026-09-11.md), not its historical results. D1/H1 overall acceptance and the deep-link issue remain open.

## Cause and correction

The live local fixture returned server-info without a version. The old health check inserted `version=null` into details, then `HealthComponentResult` called `Map.copyOf`, which rejects null values. The resulting exception had no message and was converted into **CRITICAL / Admin API unreachable**. The previous hypothesis of metadata permission denial was not the observed cause in this corrected live run: both targets returned **HEALTHY, versionAvailable=false**, without broader permissions.

The check now omits unavailable version values, records `versionAvailable`, and limits a HEALTHY result to metadata-endpoint reachability. A null response, authentication/authorization gap, unsupported metadata or unclassified check error is UNKNOWN with a bounded reason code. Adapter-reported upstream/transport failure remains CRITICAL for the request, not proof of a whole-environment outage. Raw exception messages/causes are never copied into this component result. It still performs only the existing server-info read using the selected target's credentials.

Overview projection now treats negative/malformed counts and unavailable installation/workload/pod evidence as null rather than negative or invented zero capacity. Resource-specific warnings preserve unaffected values; denied pods suppress pod/zone counts, and missing zone labels suppress zone count. Cluster-wide zone counts are not used as installation topology. The UI displays `Unknown / not collected` for null/invalid counts and preserves genuinely observed zero. Raw stored inventory sentinels and historical health results are unchanged; run a fresh health check for corrected results.

## Tested source and versions

- HEAD `572cb7b`, branch `feature/0.8.1-client-lifecycle`; existing dirty/untracked work preserved. No commit/push/rebase/tag/publication/deployment.
- [Source manifest](evidence/d1-health-source-2026-09-11.json): **516 files**, SHA-256 aggregate **6e83d00da28d06b514a4077846fe3f44af41c6498d828d66b97addc8cbc8bf32**. Aggregate input is sorted `file SHA-256`, two spaces, relative path and newline. Source includes tracked/untracked implementation, build inputs, fixtures and dependency manifests, not installed dependencies or final documentation. Unsigned local evidence, not a reproducible-build attestation.
- Versions retained: backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/package-lock root **0.8.1-dev.0**, report schema **1.1**, Flyway **V1–V10**. No dependency or permission changes. Java 21 via per-command jenv; global Java selection untouched.

## Fresh verification

| Check | Result |
|---|---|
| Baseline backend `JENV_VERSION=21 jenv exec mvn clean verify` | **358 passed**, 9 opt-in integrations skipped; BUILD SUCCESS |
| Baseline UI `npm run test:run` | **94 passed** |
| Corrected backend `JENV_VERSION=21 jenv exec mvn clean verify` | **376 passed**, 0 failures/errors; 9 opt-in integrations skipped; BUILD SUCCESS |
| Corrected UI tests / production build | **97 passed**, 16 test files; build success |
| Disposable real-identity lab | **54 checks passed**, compact-JWT log check passed, exit 0 and owned-resource cleanup |

New backend tests cover missing system/version metadata, null response, sanitized 401/403-style adapter failures, unavailable/unsupported/unclassified failures, no extra adapter calls, valid zero counts, malformed/sentinel counts, missing bindings, resource-specific collection gaps and topology scope. Three additional UI tests cover missing counts, invalid legacy sentinels and valid zeros. These are component tests, not a fresh browser recording.

The [retained live check output](evidence/d1-health-run-2026-09-11.txt) adds report-health and persisted-overview regressions for each target to the previous 50 checks. Both A/B reported the metadata endpoint accessible with **versionAvailable=false**; desired/ready replicas, pod count and zone count were null/omitted for unconfigured infrastructure. Existing report provenance, target isolation, identity negatives and actual SSE delivery still passed. No permission elevation or actual cluster access occurred. The new 54-check suite ran once; the earlier two 50-check runs belong to their original source revision.

Diagnostic logs: `/private/tmp/kcops-health-fix-baseline.log`, `/private/tmp/kcops-health-fix-ui-baseline.log`, `/private/tmp/kcops-health-fix-verify.log`, `/private/tmp/kcops-health-fix-ui-test.log`, `/private/tmp/kcops-health-fix-ui-build.log`, `/private/tmp/kcops-health-fix-live.log`; platform log directory `/private/tmp/kcops-identity.0Dr6gt`.

## Cleanup, limits and next work

Final Podman inventory: **0 containers, 0 volumes**, only default Podman network; four reusable images preserved (Community Keycloak 26.7.1, PostgreSQL 16, RHBK 26.6, Prometheus v2.55.1). Only Community Keycloak/PostgreSQL were used. Disposable database state was intentionally removed and is not recoverable; no unrelated resources or images were removed.

These corrections do not establish full dependency health, production availability, complete report correctness or RHBK/OpenShift compatibility. Other provider/engine error handling, raw inventory gaps, safe OIDC return paths, metrics-present/MCP report acceptance, real-local-identity installation confirmation, late-response negatives, dependency review and reference-agent work remain governed by H1/D1. No browser run was repeated in this correction turn; interface rendering was verified by component tests and production build. Architecture, current context, milestone progress, Unreleased and version bookkeeping were reconciled; historical ledgers remain unchanged.
