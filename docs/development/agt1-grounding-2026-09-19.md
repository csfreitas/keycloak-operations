# AGT1 — Report-bound structured grounding, 2026-09-19

Status: **local structured-grounding contract validated; full acceptance open**. This slice extends
the [profile foundation](agt1-profile-2026-09-19.md); it does not enable a model,
provider, external disclosure or full AGT1/H1/D1 acceptance.

## Delivered contract

`AssessmentTools` adds `findingDetails` version **1.0**, built only from the same
sanitized `OperationsReport` returned by `OperationsReportService`. No second
collection, history join or Markdown parsing is performed. Existing summary, score,
health, provenance and Markdown remain independent of the optional profile.

`ReportFindingDetails` examines only the first 20 source findings. Each admitted
finding is copied whole, with at most 256 value nodes, depth 6 (root 0) and 8192
UTF-16 text units including all keys/strings. JSON-shaped values, finite Float/Double
and safe primitive-wrapper integers are supported; unsafe integral values,
BigDecimal/BigInteger/custom numeric classes and arbitrary beans are omitted.
The check precedes unrestricted conversion/serialization. No partial scalar/tree
is substituted for a finding, and skipped slots are not filled from later entries.

The extension repeats report/target/assessment identity, original `sourceIndex`,
total/returned/omitted counts and limits. Missing assessment/list is UNAVAILABLE with
null totals; a known empty list is AVAILABLE with zero counts. AVAILABLE is not a
completeness or health verdict. Omitted findings may be important. Duplicate rule
keys remain distinct through report-local indices; no persisted finding/evidence
UUID is invented. These limits cover the extension, not the preexisting full envelope.

[Profile 0.2.0](../../dev/reference-agent/README.md) requires findingDetails1.0,
validates matching identities/counts/indices/limits and copies facts using exact
RFC 6901 pointers, including escaped dynamic keys, nulls and empty containers.
Finding text/references/evidence remain untrusted data, never privileged instructions,
tool requests or permission. Packet/source ownership is separate and immutable on
the client. No remote reference URL is fetched. Structural explanation checks still
require semantic review and explicitly state that no model was evaluated.

Independent review identified and corrected early array-limit checks and the
assessment-present/list-unavailable null-count contract. Descriptor inspection of
already-decoded objects is not a hard memory limit: a real host transport must cap
response bytes before parsing. The offline CLI retains its 1 MiB regular-file limit.
Repeated pointer prefixes can amplify a small evidence tree. The profile budgets
each serialized fact before appending it and checks the complete packet/fallback
against **262144 UTF-8 bytes**. Oversize rejects without clipping or recollection.
An adversarial long-key/many-leaf regression covers this independently of server limits.
Secret-pattern filtering is not universal secret/PII detection or prompt-injection
prevention. The operational instructions retain those limits, informed by
[official agent safety guidance](https://developers.openai.com/api/docs/guides/agent-builder-safety).
No hosted agent/API dependency is introduced.

Requirements: FR-AGENT-001, FR-AI-001–003, SEC-AI-001/002, SEC-MULTI-001,
SEC-REPORT-001, NFR-AI-001. Architecture: [reporting contract](../architecture/operations-reporting.md#report-bound-mcp-finding-details),
[AI boundary](../architecture/ai-assisted-operations.md), [security](../architecture/security.md).

## Source and versions

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`. Accumulated dirty work is preserved. No commit,
push, rebase, tag, release, global Java/Node change or real cluster access.

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lock **0.8.1-dev.0**,
canonical report **1.1**, REST and Flyway **V1–V10** are unchanged. MCP extension
**1.0** and profile **0.2.0** are independent contracts, not product releases.
Dependencies, issuers/grants, assessment engine, operational write state, UI source
and AGENTS invariants are unchanged. Prior dated ledgers retain their original facts.

Source comparison, fresh logs/build artifacts and final inventories are recorded in
the [manifest](evidence/agt1-grounding-2026-09-19.json). Hashes are unsigned local
consistency evidence, not authenticity/release or full evaluator attestations.

**622 source inputs**, **11 changed / 5 added / none removed** versus the profile
foundation manifest; aggregate SHA-256:

`f2ed1cbafd68347352ff77ff286af0da0748dd9b3566c1c6b7f37e4800a80516`

The changed product code is only the MCP assessment tool and its new projection;
remaining source changes are profile/tests, lab checks and their guides. The manifest
excludes ordinary docs from the source aggregate and separately records validation
logs, Maven reports, build artifacts and inventories.

Final verification matched **768 hashes**: 622 source inputs, 128 Maven reports,
10 execution logs, 6 build artifacts and 2 inventories. Offline documentation checks
passed **142 Markdown documents / 935 local links / 15 milestone specifications**,
zero errors/warnings, plus six parser self-check groups. Shell syntax and
`git diff --check` passed. These checks verify structure/local consistency, not
external URLs, factual completeness, model quality or milestone acceptance.

## Executed validation

Java 21.0.10 through per-command jenv; Node 24.19.0 through the installed local runtime.
Logs: `/private/tmp/kcops-agt1-grounding-<name>-20260919.log`; temporary logs may expire.

| Run / log | Actual result |
|---|---|
| Fresh pre-change Maven clean verify / baseline | 1684 passed / 9 opt-in ITs skipped, exit 0; completed 19:02:21 −03 |
| Focused report/projection tests / focused | 52 passed, exit 0; 21 new Java tests |
| Final Maven clean verify / verify | 1705 passed / 9 opt-in ITs skipped, exit 0; completed 19:08:53 −03 |
| UI regression / ui | 195 passed / 22 files, exit 0 |
| UI production type-check/build / ui-build | Passed, exit 0; 87 modules |
| Profile regression / profile-initial, profile, profile-final | 45, 68, then 70 passed respectively, each exit 0; prior logs retained |
| Combined Node contracts / contracts | 234 passed, zero failures/skips, exit 0; 70 profile/CLI + 164 runner/fixture invocations, including 7 installation-fixture tests beyond the prior slice's selection |
| Actual OIDC/MCP lab / lab | 85 checks, compact-JWT log scan and verified owned cleanup, exit 0 |

The new coverage consists of **21 Java tests** and **25 profile/CLI tests** (22
finding-details/budget tests and 3 CLI tests), plus 4 real-lab checks. The final lab
used the package from final Maven validation, Community Keycloak **26.7.1**, local
PostgreSQL16, Node24 host/profile0.2.0 and no model/provider. It generated only the
preexisting one MCP report per actor and checked exact report/assessment ownership,
nonempty details and evidence pointer equality, alongside existing cross-target and
invalid-identity denials. Findings and scores remain determined by the backend.

Run UUID **25253cd8-8552-42d3-afb6-132df646712a**; diagnostics
`/private/tmp/kcops-identity.ASGME8`. Cleanup removed only that run's four disposable
containers, owned network and tmpfs database data. Disposable data is not recoverable;
no persistent volume was created and no image/volume was deleted. Independent final
inventory **19:12:43 −03**: zero containers/volumes, default network only, four cached
images unchanged, all twelve fixture ports free and lab lock absent.

No opt-in Maven IT, real RHBK/OpenShift, metrics/installation lab variant, browser
walkthrough, hosted model evaluation or CI run occurred in this slice. The combined
reference-agent/metrics mode has expected total 90 but was not run. Structural/model
claims remain separate; contradictory prose still passes structural checks and
requires semantic review. Human/independent operator reproduction is not claimed.

## Limits and next step

This contract supports detailed local grounding but not retained report replay,
raw evaluator inputs, structured performance/no-traffic proof, universal filtering,
semantic narrative verification or a frozen client/model/provider combination.
Report UUIDs are generated on demand; another call makes another collection and
persists platform observations/audit, not replay. Do not silently retry or join it.

Next AGT1 gate: explicitly select the client/model and approve payload/provider scope,
then record actual authentication, adversarial/repeated model behavior and human
review. The deterministic no-AI path remains usable. Independent operator reproduction
and H1 trust limits remain open; D2 still needs the explicitly approved RHBK/OpenShift
lab. Local Community Keycloak tests do not establish supported RHBK compatibility.
