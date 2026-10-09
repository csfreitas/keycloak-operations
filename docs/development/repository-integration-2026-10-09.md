# Consolidated source integration — 2026-10-09

The operator explicitly authorized repository commit/push and then integration into
`main`, with a concise delivery description. This is a development checkpoint, not
a release, image publication or deployment. No rebase or force-push is used.

Initial local HEAD was `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`, with **543 pending files / 425 status entries**.
The accumulated documented slices depend on one another; publishing only the last
ONB1 files would omit prerequisites. A fresh fetch found `main` at
`4cac56cf0ba5f8669068e5d9c91d53621c1146b3`, with the same tracked tree as initial HEAD.
The working branch was fast-forwarded to that merge without changing file contents.
Git and the PR are authoritative for the subsequent commit/merge outcome.

## Delivered source scope

- Security, target isolation, conservative evidence/health/metrics and audit hardening.
- Explicit installation discovery/binding and authenticated console improvements.
- Restricted realm/client reads and separately versioned reference clients/lab hosts.
- ONB1 preflight plus configuration ownership, ORM revisions and atomic audit.
- Architecture, requirements, roadmap, milestone status and historical evidence.

The publication review covered changed-file inventories, bounded credential/artifact
scans, documentation destinations and risk-based code review of trust boundaries.
No publication blocker was identified after the dependency correction below.
This is not an exhaustive semantic audit, independent operator reproduction or H1
acceptance. Dated evidence and failed historical results were not rewritten.

The selected checkpoint contains **544 files**. Relative-link/fence checks cover
**155 Markdown files**, **1293 destinations** and **322 balanced fence markers**.
Staged source/document whitespace checks pass apart from four pre-existing trailing
spaces in the retained raw `h1-inventory-envelopes-runs-2026-09-18.txt` and
`h1-scrape-readiness-runs-2026-09-18.txt` compiler outputs. Those bytes are intentionally
preserved as historical evidence, not silently reformatted to hide the check result.
No local prompts, credential files, build output or diagnostic temporary directories
are included in the staged set.

## Fresh validation

| Check | Observed result |
|---|---|
| Java 21 via per-command jenv, `mvn clean verify` | **2117 passed**, 0 failures/errors/skips; **9 optional ITs skipped**; BUILD SUCCESS, 2m04s, finished `2026-10-09T16:22:27-03:00` |
| Node **24.19.0**, all reference/client/fixture/runner unit suites | **549 passed**, 0 failures/skips; no AI or real provider calls |
| UI before dependency correction | **232 passed**, build successful |
| Initial locked npm audit | **1 high** finding: `source-map-js` 1.2.1 |
| Corrected lockfile install, `npm ci --ignore-scripts` | Passed; direct package manifest unchanged |
| Corrected UI tests/build | **232 passed** in 23 files; TypeScript/Vite build passed |
| Final `npm audit --audit-level=moderate --ignore-scripts` | **0 reported vulnerabilities**; limited to this npm inventory/advisory response |

The default Homebrew aliases named node@22/node@24 actually resolved to Node 25.6.1;
the validation explicitly used the available bundled **24.19.0** instead. No global
runtime selection was changed. Maven used `JENV_VERSION=21`, with all three provider
IT opt-in variables unset and explicit Podman `podman-machine-default-root`.
Existing compiler and Quarkus REST/RESTEasy Classic warnings remain.

Local diagnostics are retained under
`/private/tmp/keycloak-operations-publication-20261009.uH8lSx/`: `maven.log`, `node.log`,
`ui-tests.log`, `ui-build.log`, `npm-audit.log`, `npm-ci.log`, `npm-audit-fixed.log`,
`ui-tests-fixed.log`, `ui-build-fixed.log`. These are not portable release artifacts.

Final Podman inventory: **0 containers, 0 volumes**, default `podman` network, same
cached Keycloak 26.7.1, PostgreSQL 16, RHBK 26.6 and Prometheus v2.55.1 images. Scoped
test cleanup removed disposable resources; no global prune, VM restart or unrelated
resource deletion. No real OIDC/browser/Keycloak/RHBK/Prometheus/OpenShift acceptance
was repeated locally in this publication slice; prior live ledgers remain historical.

## Dependency correction and limits

`vite → postcss → source-map-js` resolved to 1.2.1 in development tooling.
[GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q) identifies
indexed-source-map event-loop denial of service; upstream
[1.2.2](https://github.com/7rulnik/source-map-js/releases/tag/v1.2.2) supplies the fix.
Only the package's lockfile version/tarball/integrity were changed, within the existing
PostCSS semver range. No application-runtime exploit was demonstrated. The initial
nonzero audit remains recorded, rather than replaced by the final clean result.

Backend **0.8.1-SNAPSHOT**, UI **0.8.1-dev.0**, AGT1 **0.2.1**, AGT2 client **0.1.0**,
preflight **0.1.0**, observation **1.0**, report **1.1**, findingDetails **1.0** and
Flyway **V1–V11** remain the development versions. No tag/release is produced.

**V11 must not be deployed over retained populated installations with legacy/config
ID collisions without governed adoption/restore.** That workflow is not implemented;
mixed-version writers and image-only rollback remain unsupported. Main integration
does not waive these [upgrade limits](../architecture/registry-ownership.md).
ONB1/AGT2 remain PARTIAL, OP1 PLANNED; H1/D1/AGT1 and release acceptance remain open.
