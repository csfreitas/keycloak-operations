# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- ONB1 configuration-ownership foundation (ADR 0014): additive Flyway V11 classifies historical targets as LEGACY_UNCLASSIFIED and adds an ORM registry revision; only newly bootstrapped targets are CONFIGURATION-owned. Effective changes and binding invalidation share mandatory SYSTEM audit and a transaction; normalized restarts are no-ops. Database/composite no longer falls back to configuration. Populated upgrades with ownership collisions require reviewed adoption; no automatic takeover, public registration, grants or global read-only/BIND change

- ONB1 administrative preflight contract 0.1.0: default-closed REST with exact verified administrator/REST-client matching, bounded closed drafts, local target collision/reference checks and sanitized inconclusive results. No registration, grants, credential resolution, candidate network requests, UI/MCP, migration or read-only/BIND change. ADR 0013, negative unit/HTTP tests and continuity document this partial milestone; product versions remain development versions

- Documented Operator-managed installation with a portable Operations hub (ADR 0012), draft installation contract 0.1 and deliberately non-deployable example. Added planned OP1 acceptance and configuration ownership/security requirements; reconciled architecture, roadmap, milestones and agent context. No controller/CRD/bundle, application behavior, deployment, permission, dependency or product-version change

- Authenticated local host exercise for the unchanged AGT2 client 0.1.0, using both restricted human PKCE identities and fixed loopback MCP. The adapter binds bearer/context/session, verifies negotiated protocol and response IDs, bounds finite JSON/SSE, and explicitly terminates sessions; the lab HTTP reader now propagates caller cancellation. New offline tests and real checks cover permitted facts/references, denied scopes, stale pins, replacement operators and held-reply rejection. This is not a general production transport, IdP revocation, source mutation or AI evaluation

- Independent AGT2 reference client/answer contract 0.1.0: two-tool configuration catalogue/read orchestration, explicit scope selection, closed observation validation and fact references, shared time/call/byte/concurrency budgets and session-context invalidation. Synthetic no-network CLI and offline CI tests use no AI; a non-cooperative cancelled transport blocks overlap until settled. Real authenticated host/MCP integration remains pending; AGT1 0.2.1, server/UI, grants and product versions are unchanged

- Opt-in authenticated AGT2 configuration lab with two restricted human PKCE identities, distinct REST/MCP clients, minimal source-reader fixture, pinned resource IDs and owned tmpfs lifecycle. The dated ledger records real console checks and 55 live authorization/parity/provider checks, plus failed harness attempts and their cookie/catalogue-decoding corrections. AGT2 remains partial; no product source, default grant, version, model or RHBK/OpenShift acceptance change
- AGT2 configuration observation contract 1.0: shared restricted realm/client read service, two explicit MCP tools and REST endpoints, plus standalone `/configuration` console. Exact role/verified JWT client/channel grants pin target, internal realm/client identities and closed Boolean fields; absent fields remain unknown. No scopes or model enabled by default, no expansion of AGT1, and no implicit narrowing of legacy target roles
- Cooperative ten-second collection budget, sanitized unavailability, pre-provider denials, no-store REST responses, attribution-only optional audit, scope/session-safe UI and synthetic identity/provider/browser regression coverage. Question matrix, onboarding/migration limits, architecture, milestone and evidence ledger document this partial local slice; live OIDC/RHBK and full AGT2 gates remain open

### Fixed

- Reference profile 0.2.1 aligns operational instructions, descriptor and guide with the evaluator's unchanged 20-item, 1000-UTF-16-unit and 1–10-reference bounds. Shared constants and local regressions detect drift; old 0.2.0 responses remain invalid and are not relabeled or repaired. An offline event reader separates Codex client diagnostics from observed tool items and marks malformed/unknown/incomplete traces inconclusive. CI runs the offline profile tests; no new model request, product-version change or AGT1 acceptance is claimed
- Change detail controls/results now belong to one route visit; late reads/actions/errors cannot replace another change or release its pending controls. Duplicate submissions are synchronously blocked, retries hide stale controls, and incompatible response identities are rejected
- Change lists isolate target/filter visits, discard obsolete responses and reject cross-target records; clearing the target cannot restore a stale table. Unselected filters and secondary actions use the existing readable button style; filters expose selection through `aria-pressed`
- A loopback-only synthetic change-navigation fixture exercises the actual UI with delayed/denied/mismatched replies, without backend credentials or real administrative writes. This is UI regression evidence, not new authorization or production-write acceptance

- Browser REST/SSE transports are bound to the current authentication generation: sign-out/provider changes abort old work and reject late tokens, responses, decoded bodies and events, preventing old subscriptions from reconnecting under a new identity
- Current REST/SSE 401 invalidates local authentication and removes protected routes; target 403 preserves the valid session, and stale 401 cannot sign out a replacement session. This is local transport/presentation protection, not immediate IdP/JWT revocation or rollback of server work already dispatched

- Environment discovery REST now checks target READ before collection; authenticated callers without that target grant no longer reach the discovery provider
- Ten non-change MCP tool families share safe error projection: known codes retain filtered explanations, prebuilt tool errors lose cause chains, and unexpected failures use fixed diagnostics. Existing controlled-change error boundaries remain separate
- Read-only Keycloak DTOs and selected target/fleet/overview/inventory/metrics REST/MCP responses filter recognizable credential metadata in copies; registered target/correlation identities and raw collector/rule inputs remain unchanged. Typed topology/assessment count maps preserve numbers even when a label resembles a secret field
- Health metadata is filtered after engine evaluation before new retention and on historical detail reads without rewriting old rows, statuses or event scope. Identity lab now verifies own/foreign/unmapped environment access through real OIDC

- Controlled-change planning rejects recognizable credentials before provider reads/persistence; unsafe observed or executable legacy state requires replanning rather than redaction into applied values. Full client updates clear secret and registration-access-token fields and refuse unsafe unselected metadata; unsafe read-back cannot become VERIFIED
- Change history and idempotent lifecycle responses use sanitized copies without rewriting stored state, fingerprints or canonical provenance. Rejection/verification/result descriptions and optional audit payloads are filtered; audit scope and target/trace correlation remain exact
- REST McpException and controlled-change MCP messages hide recognizable credential text while preserving status/code; MCP cause chains and audit persistence-failure diagnostics no longer expose raw exceptions. Mandatory installation success audit remains transactional; generic-tool/identity/unknown-secret limitations remain explicit

- Reports sanitize structured metadata before Markdown rendering; recognizable credential text, header values and secret-bearing dynamic keys are filtered without dropping colliding evidence entries. Rendering neutralizes HTML/fence/control context and labels metadata as untrusted data, not authority
- New snapshots hash and persist sanitized inventory; historical snapshot/finding/diff reads use safe copies without rewriting retained bytes or hashes. Assessment findings and immediate results are projected only after deterministic evaluation; desired/applied change state is not rewritten by output redaction
- Metadata patterns preserve operational prose/typed facts and bound problematic regex paths; this is scoped recognizable-secret protection, not universal secret detection or prompt-injection prevention

- Cluster inventory and Route/config collection use the same bounded observed API-version capabilities rather than configured runtime hints; failed discovery and unsupported advertised versions remain explicit gaps, and independent valid observations survive
- Infrastructure assessment, snapshots and API health share a conservative completeness contract; report sections require explicit coverage, so warning-free unknown/legacy evidence cannot become COMPLETE or HEALTHY. Configured VM remains unsupported rather than unconfigured

- Local runner cleanup now resolves a transient process-group probe/signal error only after independent group-absence verification; persistent unknown/present groups still fail and retain the lock, while original failures/timeouts/interruptions are preserved

- Local identity/installation runners bound process execution, startup, readiness and cleanup; use an explicitly selected/pinned local Podman endpoint and per-run ownership labels; remove verified full IDs without pruning images/volumes; preserve the lock when cleanup is unconfirmed
- Runtime existence errors no longer mean absence; only recognized transport failures on reads/scoped cleanup receive limited visible retries, while ambiguous startup and original test/interruption failures remain failures; no automatic VM restart

- Scoped Admin collection rejects oversized/malformed token and Admin responses before model defaults, including chunked/gzip bodies, duplicate identities/JSON fields, invalid required types and mismatched realm details; failed realm lists remain partial without invented empty/count evidence
- Collection transport refuses unsafe DEBUG/TRACE diagnostic categories without changing operator logging, disables unnecessary native HTTP authentication/cookies, and exposes fixed cause-free adapter errors; ordinary administration and controlled writes are unchanged

- Report, snapshot, health, assessment, inventory and metrics follow-on collections share a target-bound monotonic deadline; same-target nesting cannot renew it, different-target scopes reject, and late/unexecuted observations remain explicitly partial/unknown
- Scoped Admin reads have a separate cached client with request-local timeouts and response checkpoints, preserving ordinary/controlled-write transport; partial assessments no longer advertise an unavailable numeric score in events
- Earlier validated observations survive budget aborts; initially unavailable inventory no longer exposes ambiguous empty pods/zero topology, and management health retains prior observed failures without inventing complete coverage

- Infrastructure inventory, discovery, installation candidates and ServiceMonitor reads validate raw typed envelopes and explicit resource identities before model defaults; missing/invalid/truncated collections cannot establish absent resources, while valid typed-list item TypeMeta omission remains supported
- Fixed resource reads share per-response byte/parser/cardinality/deadline bounds; inventory reuses one node observation, preserves independent evidence, and rejects contradictory Pod readiness and missing policy/networking structures
- Failed API discovery remains UNKNOWN, never confirmation of a configured runtime; version failures retain independently observed platform classification

- Scrape readiness evaluates every scoped binary `up` value instead of counting series; a present failed scrape cannot appear healthy, mixed failures remain visible, and missing/invalid/stale observations remain unknown
- Scrape queries always pin the registered `target_id`; returned observations validate scope, cardinality, timestamps and uniqueness without exposing labels or provider diagnostics
- ServiceMonitor evidence requires explicit installation identity and exclusive same-namespace Service association, bounded monitor lists and a supported unambiguous endpoint; malformed/omitted list fields, shared selectors, invalid durations and permission failures cannot establish favorable configuration evidence

- Compound Prometheus summaries/categories share a configurable monotonic collection budget; slow headers consume body time, late results are discarded, interruptions retain their flag and no further queries start after abort
- Timed-out metric collection is explicitly partial in reports/required-metrics assessments; failed presence probes stay unknown and do not poison caches or imply absent histograms/idle traffic; aborted collectors omit unproven source availability
- Configured metrics credential-resolution failures reject collection before transport instead of falling back to anonymous access; intentionally anonymous configurations remain supported

- Inventory warnings now use fixed safe messages and allowlisted resource identifiers, including on deserialization; provider exception text is not retained, and unknown warning resources fail closed
- Inventory assessment mapping preserves known zero/false observations but withholds missing/denied/incomplete sections, exact-count failures and incomplete topology; partial collection suppresses available scores and high confidence even when all selected rules evaluate
- Prometheus semantic results reject invalid timestamps, incomplete evaluation grids, nonfinite subsets and ambiguous aggregate series; sustained database-awaiting evidence requires a validated range, never instantaneous fallback; NO_TRAFFIC needs corroborating zero request rate and known empty percentile evidence

- Health aggregation now deterministically preserves UNKNOWN inputs; missing/malformed management and required inventory evidence cannot become a healthy result, and selected provider/engine failures expose safe reason codes rather than exception details
- Management-health and Prometheus reads cap received body bytes and body duration, constrain JSON structure, reject redirects and avoid raw provider-error output; these controls do not establish a whole-operation deadline or complete metrics coverage
- Failed range queries no longer manufacture window averages/maxima from instant samples; wrong result types, partial-response warnings and excessive per-series samples remain unavailable; generic Java-rule NOT_EVALUATED/SKIPPED results no longer inflate evaluated/matched counters

- Disposable identity-lab log checks now fail when JWT scanning cannot complete, instead of treating scanner errors or missing logs as a clean result; detected contents remain withheld

- Operations reports project uncollected/denied infrastructure as null instead of legacy count/presence defaults, preserve independent successful observations and genuine zeros, and leave retained snapshots/hashes unchanged

- OIDC login restores an allowlisted internal deep link using a bounded tab-local navigation hint, without expanding callback origins or retaining query/fragment/token material; denied target overviews no longer mount child pages

- Admin API health no longer labels authentication/authorization or missing metadata as an outage, does not propagate provider exception messages, and avoids null-version failures in immutable result details
- Target overview projects uncollected/denied infrastructure counts as unknown, rejects negative/malformed sentinels, preserves observed zeros and no longer substitutes cluster-wide zones for installation zones; UI labels missing counts explicitly

### Added

- AGT2 planned product direction: keep the operator application as a first-class non-AI inspection/assessment/health/report interface and add optional question-driven infrastructure/IAM assistance through MCP. ADR 0011, five requirements and shared UI/REST/MCP policy/evidence design bring fine-grained read access forward from P2; current target-wide grants and AGT1 report-only profile remain unchanged. No new tool, permission, model runtime, product version or live acceptance is delivered by this documentation addition
- SecOps & IAM Co-pilot backlog catalogue: IAM-06 organizational password-age assessment and SECOPS-01 bounded identity-event investigation under P1; HLP-01 temporary access and SECOPS-02 separately approved containment under P2. Four future requirements and 39 synthetic acceptance cases retain version/source uncertainty, privacy, scope, expiry and approval/recovery boundaries. Offline catalogue checks are wired to CI, not evidence of implemented user tools, runtime policy enforcement or model acceptance; current read-only reference profile and product versions remain unchanged
- Authorized AGT1 synthetic model-trial evidence: three Codex responses using requested gpt-6-astra, all facts preserved but 0/3 structural acceptance because reference lists exceed the undocumented instruction limit of 10. Agent-assisted review found no semantic/security violation in these samples; no general safety or MCP integration claim. Raw responses/events are retained, client diagnostics distinguished from tool calls, and instruction/validator alignment is the next local task. Product/profile versions and code are unchanged
- AGT1 synthetic-trial preflight evidence: three prepared cases, 70 passing local profile tests and 12 local packet/negative checks. Codex client startup failed before model events; automatic review rejected the execution retry pending explicit operational-instructions disclosure approval. No model capability, MCP client integration, product change or completed AGT1 acceptance is claimed
- SCORE1 backlog/design: proposed explainable 1–100 operational rating (operator-confirmed scale), reviewed control weights/critical caps, separate coverage, product/version applicability and public lifecycle/patch/CVE evidence with RHBK backport-aware matching. Roadmap, requirements, formula/example and architecture specify acceptance; no scoring code, feed connector or runtime capability is delivered. Current 0–100 scoring and all product/contract versions remain unchanged
- Additive MCP findingDetails1.0: bounded whole sanitized findings/evidence from the same generated report, report/assessment binding, original source indices and explicit omission counts. No history join, second collection, new grant, retained report/replay or REST/report-schema change
- Optional provider-neutral reference profile 0.2.0: one fixed report tool/operator-selected target, provenance and bounded finding/evidence projection, deterministic no-AI fallback, offline CLI and exact-fact/reference checks. This revision requires findingDetails1.0; finding text remains untrusted data, while envelope metadata/Markdown stays excluded. Structural success still requires semantic/security review. Real-identity MCP lab rehearsal checks evidence pointers; no model/provider, external disclosure or product-version change

- Disposable real-identity browser-negative fixture and supervised runner mode: wrong issuer, wrong audience and expired signed tokens are observed through the actual UI/IdP/backend, followed by a valid control. Two restricted public PKCE lab clients add no grants; expiry uses an explicit bounded transport delay. Sanitized observations contain only booleans/status, never tokens; product code and versions are unchanged

- Discovery retains configured type separately from observed runtime and Route/config v1 advertisement; new inventory snapshots and report summaries include discovery coverage without adding permissions, migrations or a host/container collector

- Disposable installation validation with real local OIDC identity, a bounded loopback synthetic Kubernetes API, separate setup principals, explicit candidate selection and UID replacement/permission-denial controls; automated checks cover binding isolation, stale/replayed/expired runs, global read-only denial and mandatory database audit without cluster writes
- Standalone fixture and runner-helper checks; both identity runners now share an ownership lock that preserves existing/stale locks and only releases a verified owned lock

- Optional disposable Prometheus identity-lab profile with real scoped JVM metrics, authenticated MCP report generation and cross-target report denial checks; missing telemetry and inconclusive assessments remain explicit

- D1 local runner extensions for real target-filtered SSE delivery, authenticated reports and cross-target assessment history denial; opt-in browser lab with bounded lifetime and owned-resource cleanup

- Target-bound operations reports combining sanitized platform inventory, health, deterministic assessment, actionable findings, and optional semantic performance metrics
- Shared REST `POST /api/v1/targets/{targetId}/operations-reports` and MCP `keycloak_generate_operations_report` surfaces
- AI-assisted operations architecture with deterministic backend decision boundaries and evidence provenance
- Typed client redirect URI and Web Origin planning through shared MCP and REST application services
- Typed client security and flow planning for PKCE, Authorization Code, Implicit, Direct Access Grants, service accounts, and public/confidential semantics
- Typed, secret-free client creation plus controlled enable/disable planning and verified apply
- Deterministic URI validation, normalized set diffs, transition-aware risk/policy, stale-plan checks, apply, and read-back verification
- Structured change-operation persistence and fingerprinting with compatibility for existing 0.8 scalar records
- Real community Keycloak 26.7.1 CI smoke path covering PostgreSQL, Prometheus, MCP reads, health, environment discovery, and operations-report generation
- OIDC/PKCE browser implementation with in-memory tokens, refresh, authenticated SSE and sign-out; disposable two-target identity validation lab (full browser/IdP acceptance still open)
- Explicit infrastructure credential modes without ambient fallback; exact installation API/kind/name/UID, ownership-scoped inventory and Service/Ingress/Route association
- Existing-target Installation REST/UI with expiring retained candidates, DISCOVER/BIND permissions, UID/context/revision revalidation and mandatory transactional confirmation audit; no cluster writes or new target/connection registration
- Flyway V9 exact installation binding and V10 managed revision/discovery runs; bootstrap preserves managed bindings until relevant connection configuration changes
- Report schema 1.1 collection windows, independent-section mode and packaged rule-catalog hash; replay remains explicitly unavailable
- Fifteen executable roadmap milestone specifications, extension requirements, documentation index/review ledger and release-versioning workflow

### Changed

- Explicit `redactMetadata` is separate from legacy structural filtering used by controlled-change state. Historical and new sanitized snapshot sub-hashes may differ by policy. Development versions, report schema 1.1 and V1–V10 remain unchanged; no database migration, dependency or permission change

- Legacy cluster wrapper type/OpenShift helpers are deprecated configured-only handles and perform no implicit detection; production inventory uses bounded fixed-path reads. Legacy constructors remain source-compatible but missing discovery is incomplete. Development versions, dynamic report schema 1.1 and V1–V10 remain unchanged for this unreleased corrective slice

- Scoped Admin collection caps response bodies at 1 MiB, successful token bodies at 64 KiB and realm/client lists at 500 items. Known schema validation is conservative, not a complete Keycloak schema or full-environment coverage guarantee; unsupported responses yield partial/UNKNOWN evidence

- `collection.operation-timeout-ms` adds a shared cooperative collection deadline (default 30000 ms, maximum 120000 ms); transport/body deadlines inherit its remaining time. Persistence/rendering and already-blocked I/O are not a hard wall-clock SLA. Development versions, report 1.1, migrations and permissions remain unchanged

- Explicit infrastructure clients no longer retry requests automatically or enable automatic redirects; existing authentication/TLS/proxy configuration is retained. Large or unsupported responses now yield partial/UNKNOWN evidence. These are per-response controls, not a whole-inventory/report deadline; product versions and public API/schema/permissions remain unchanged

- Corrective dependency update: Quarkus BOM/plugin 3.39.4 and MCP 1.13.2; Keycloak Admin/common clients resolve consistently to 26.0.12 without an extra transitive override
- UI routing/build/test stack upgraded to Router 7.18.4, Vite 7.3.6, Vitest 4.1.11 and React plugin 5.2.0, preserving React 18 and application/OIDC/test sources; engines now support Node 22.12+ within 22.x or Node 24.x, with Node 24 LTS selected for CI and Docker builder; Vite's default browser build target changes as documented

- The 2026-09-18 installation slice changes only lab fixtures, runners, checks and documentation: backend/UI product code, ordinary reader/default grants, dependencies, development versions, Flyway V1–V10 and report schema 1.1 remain unchanged
- Development versions aligned to backend/application/MCP/OpenAPI `0.8.1-SNAPSHOT` and UI/lockfile `0.8.1-dev.0`; deployment image templates aligned without publishing images or declaring a release
- Identity fixture explicitly disables inherited Prometheus settings and uses a dedicated exact UI origin on loopback port 18300, with a 45-second browser access token for refresh validation

- Packaged defaults fail closed; unauthenticated access is limited to explicit local-lab/dev/test profiles. Platform identity, backend audience and exact role/target grants are required for authenticated operation
- Approval/rejection now require APPROVE independently of WRITE; global read-only also blocks APPROVE and installation BIND. Caller actor/approver strings are not identity
- Legacy scalar client updates are metadata-only; PKCE is owned by typed security operations. V8 binds plans to policy/context/integrity; pre-context pending plans require replanning with a new idempotency key
- Infrastructure now requires exactly one explicit credential mode and namespace; no global discovery, default kubeconfig or automatic service-account fallback. Static kubeconfig excludes executable/dynamic providers and external certificate/key paths
- Documentation now distinguishes local implementation, dated live validation, planned collectors and actual milestone acceptance. Artifact versions are unchanged by the documentation-only review

### Fixed

- Realm-scoped evidence and incomplete-data score handling; provider denial/truncation no longer supports favorable report conclusions
- OIDC runtime tenant activation, safe provider-error logging and JDBC-safe SSE subscription
- Same-plan lifecycle locking and normalized-intent idempotency/context checks; these do not provide durable exactly-once remote execution

### Security

- Publication-time npm audit identified `source-map-js` 1.2.1 (transitive development tooling) under [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q). The lockfile pins patched 1.2.2 without changing direct dependencies or product versions; source-map denial-of-service is not claimed as a demonstrated runtime exploit in this application

- Reviewed npm dependency findings corrected: retained full/runtime post-update audits report zero vulnerabilities; CI now fails on moderate-or-higher npm advisories. Quarkus is updated beyond the reviewed OIDC advisory's patch floor; broad backend/image/JDK/OS scanning and H1 acceptance remain separate open gates

- Original dated dependency review retains npm audit JSON, resolved packaged inventory and primary-source applicability decisions; its selected version findings are addressed by the subsequent correction above, while comprehensive scanning/maintenance review remains open, with no forced upgrades or blanket vulnerability-free claim

- Report sections degrade explicitly without returning raw provider exception details; structured and Markdown output are redacted
- Production defaults deny path-wildcard redirects, non-loopback HTTP additions, and the Web Origin `+` sentinel
- Production defaults deny enabling Implicit flow or Direct Access Grants, weakening PKCE, and switching a client to public
- Client updates preserve unrelated configuration and clear secret material before outbound Admin REST writes
- Client creation defaults to disabled and rejects supplied secrets, duplicate identifiers, and public service-account combinations
- Target/fleet/history/audit/SSE visibility is authorization-scoped; raw MCP traffic logging is disabled and trusted identity is used for audit provenance
- Installation binding, discovery-run consumption and success audit persist atomically; optional operational-audit settings cannot disable this success record

### Validation and remaining limits

- Observed cluster capability/source coverage: [19 September evidence](docs/development/h1-capability-coverage-2026-09-19.md), baseline 1242 and final 1304 backend tests passed, 9 opt-in ITs skipped; 243 focused tests passed after corrected fixture-only failures. Final-package local labs, cleanup and source hashes are recorded separately; no new UI/browser or real-cluster acceptance. H1/D1 remain open

- Subsequent [19 September runner validation](docs/development/d1-runner-validation-2026-09-19.md) passed 138 harness tests and all three owned local scenarios with automatic cleanup. This closes the local runner follow-up, not H1/D1 or the investigation of the original VM/SSH instability
- Authorized local recovery: [new evidence](docs/development/h1-admin-boundaries-recovered-2026-09-18.md) records explicit forced-power-off approval, same-machine recovery, exact lab-resource ownership and cleanup. Fresh default 70 checks/JWT scan passed, but runner exit 1 preserves a cleanup SSH EOF; separately retried cleanup exited 0 and final inventory/ports/lock were clean with four reusable images preserved. Code/package hashes were unchanged; the later runner validation above addresses its operational follow-up

- Previous Podman recovery attempt: normal restart was authorized, but normal CLI/API stop did not stop the local VM. Forced power-off was rejected before execution pending explicit data-loss-risk approval. No new tests, cleanup, forced deletion or machine reset in that attempt; [original recovery evidence](docs/development/h1-admin-boundaries-recovery-2026-09-18.md) preserves its failed result

- Historical runtime blocker: metrics 75 and installation 83 checks passed with cleanup, but the original default lab failed after 56 checks; independent Podman API/VM checks stopped responding and cleanup was incomplete at that checkpoint. The later authorized recovery above records actual cleanup separately without changing the failed original result

- Latest Admin-boundary slice: [2026-09-18 evidence](docs/development/h1-admin-boundaries-2026-09-18.md), baseline 1050 and final 1242 backend tests passed, 9 opt-in ITs skipped; 192 added invocations. The ledger records the corrected gzip compatibility failure and local laboratories. Source: 571 inputs; six existing files changed, seven added, none removed versus the shared-collection manifest. Versions/UI/dependencies/grants/migrations unchanged. Dynamic logger reconfiguration, broader metadata/source coverage and remaining H1/D1 acceptance stay open

- Latest shared-collection slice: [2026-09-18 evidence](docs/development/h1-compound-collection-2026-09-18.md), fresh baseline 970 and final 1050 backend tests passed, 9 opt-in ITs skipped; 80 additional invocations. The ledger records corrected interim compilation/fixture/contract failures, final-package labs and cleanup separately. Source: 564 inputs; versions/UI/dependencies/grants/migrations unchanged. No hard response-time, third-party DEBUG log-safety, new browser or real-cluster acceptance claim; H1/D1 remain open

- Latest inventory/temporal correction: [2026-09-18 evidence](docs/development/h1-evidence-temporal-2026-09-18.md), fresh baseline 496 and final 561 backend tests passed, 9 opt-in ITs skipped; three interim new-test stubbing errors corrected without removed assertions. Local lab evidence is recorded separately. UI/build/dependencies/permissions/migrations unchanged; no new UI/browser run or real-cluster claim. Complete returned evaluation grids do not prove complete raw scrapes/instances or a whole-operation deadline

- Latest dependency correction: [2026-09-18 migration evidence](docs/development/h1-dependency-fix-2026-09-18.md), fresh 496-test backend baseline and final regression, 9 opt-in ITs skipped; 133 UI tests/build on Node 24.21.0; zero-finding npm audits; real local identity/metrics, browser login/renewal/isolation and synthetic-cluster installation regression. Product versions/schema/migrations/grants unchanged, previous source changes preserved; no CI execution, image build, real-cluster or blanket vulnerability-free claim

- Previous corrective slice: [H1 failures and response bounds, 2026-09-18](docs/development/h1-failure-bounds-2026-09-18.md): baseline 392 and final 496 backend tests passed, 9 opt-in ITs skipped; 133 UI tests and both builds passed; local real-identity runs passed 75 metrics / 70 default checks plus JWT log verification. Backend source changed in that slice; UI/dependencies/grants/schema/migrations and development versions did not. Podman finished with no containers/volumes and reusable images preserved

- Previous local workflow slice: [real identity and synthetic-cluster installation validation, 2026-09-18](docs/development/d1-installation-identity-2026-09-18.md), with that slice's backend/UI regression and builds, fixture/helper checks, repeated automated installation runs and verified browser selection/review/confirmation. Database evidence confirms the browser actor, consumed run and before/after UID audit; the ordinary reader retains disabled setup controls, the other target is unchanged and the synthetic cluster receives no writes. Earlier [installation implementation validation](docs/development/installation-onboarding-2026-09-11.md) is preserved separately
- H1/D1 remain open for broader inventory/evidence trust and operation/temporal-metrics bounds, dependency scanning/maintenance review, browser negatives/revocation and the AGT1 prototype. Historical RHBK fixture validation and current synthetic-cluster evidence do not establish real OpenShift compatibility; D2 still requires a dedicated approved cluster. Populated upgrades, durable remediation, new registration, host/container collectors, replay, IAM analytics and SPI execution retain their own gates
- This Unreleased section describes the consolidated development checkpoint; source publication/integration does not imply a release, deployment or completion of the remaining acceptance gates

## [0.6.1-SNAPSHOT] — 2026-08-07

### Added

- Metrics query bounds enforcement (`max-range`, `max-series`, `max-points`, `stale-after`)
- `query_range` temporal summary for Agroal awaiting (current/avg/max)
- Performance policy wiring: p95 SLO, DB awaiting critical, GC pause, cluster size consistency
- Histogram presence via bucket series; `NO_TRAFFIC` vs histogram missing
- ServiceMonitor / scrape readiness evidence (best-effort)
- Stronger multi-target / credential isolation tests; lab-b Prometheus binding
- Kubernetes deploy RBAC aligned with OpenShift (no Secret list/get)

### Changed

- Artifact / app version **0.6.1-SNAPSHOT**
- Management port references standardized to **9001**; MCP HTTP **8081**

## [0.6.0-SNAPSHOT] — 2026-08-07

### Added

- Semantic metrics stack: `MetricsService` (status/summary/category), `PerformanceSummary`, availability cache
- Real `MetricsEvidenceCollector` + performance rule pack (`KC-PERF-*`) and performance profiles
- REST metrics: `/status`, `/summary`, `/http|database|jvm|cache|authentication|runtime|cluster`
- MCP tools: `keycloak_get_metrics_status`, `keycloak_get_performance_summary`, `keycloak_get_metrics`
- Lab Prometheus compose + `scripts/smoke-metrics.sh`
- Docs: metrics-catalog, prometheus-integration, openshift-monitoring, performance-assessment, performance-slo

### Changed

- Artifact / app version **0.6.0-SNAPSHOT**
- Metrics are optional for assessment completeness unless a performance profile requires them
- No raw PromQL accepted from REST/MCP

## [0.5.0-SNAPSHOT] — 2026-08-07

### Added

- Assessment depth: HA / security / production / capacity / admin-security YAML rule packs
- Profiles: `rhbk-openshift-production-ha`, expanded `AssessmentProfile` metadata, profile resolver
- MCP tools: `keycloak_run_assessment`, `keycloak_health_check`, profile/assessment/findings list/get
- REST `GET /api/v1/assessment-profiles`
- Flyway V6: evidence completeness, confidence, category scores, rule counters, finding subject, health `duration_ms`
- Inventory evidence: readyBelowDesired, zone/node concentration, resource present flags, probes
- Docs: health-check, assessment-profiles, rule-catalog, scoring

### Changed

- Artifact / app version **0.5.0-SNAPSHOT**
- Assessment summaries expose completeness, confidence, category scores, finding counts
- PASS / SKIPPED / NOT_EVALUATED findings are not persisted as lifecycle OPEN

## [0.4.0-SNAPSHOT] — 2026-08-07

### Added

- Real per-target `InfrastructureClientFactory` (OpenShift/Kubernetes, token/kubeconfig/in-cluster)
- Target-aware `EnvironmentDiscovery` and structured `EnvironmentInfo`
- `InfrastructureInventory` + `InventoryService` (workload, pods, topology, HPA, PDB, resources, networking)
- MCP `keycloak_get_inventory`; REST `/environment`, `/inventory`, `/topology`
- Evidence catalog and collectors that emit stable keys with `targetId`
- Snapshots persist sanitized inventory (configurationHash / runtimeStateHash)
- Rule pack index (`rules/index.yaml`), profile pack filtering, duplicate rule-id detection
- GitHub Actions CI (`mvn -B clean verify`)
- Fabric8 kubernetes-server-mock isolation/inventory tests
- Docs: infrastructure-inventory, infrastructure-authentication, evidence-catalog

### Fixed

- Minimum replicas policy aligned to threshold **2** (KC-OCP-HA-001)
- Target registry list order preserved (`LinkedHashMap`)
- Test HTTP ports randomized to avoid local port clashes

### Security

- TLS verification on by default (`trust-insecure=false`)
- Secrets omitted from namespaced RBAC; Secret contents never inventoried
- Infra clients fingerprinted per target (no cross-target credential reuse)

## [0.1.0] — 2026-08-07

### Added

- Quarkus 3.38.1 MCP server (`keycloak-operations-mcp`) with Streamable HTTP transport
  (Quarkiverse MCP Server 1.13.1) and optional STDIO Maven profile.
- **Multi-target**: `Target` / `TargetRegistry` / `TargetResolver` / `CredentialProvider` /
  `KeycloakClientFactory` — one MCP manages many Keycloak/RHBK environments via `targetId`.
- Target tools: `keycloak_list_targets`, `keycloak_get_target`, `keycloak_find_targets`.
- Read-only Keycloak Admin API tools (all require `targetId`): server info, realms, clients,
  users, groups, roles.
- Stable Admin API adapter with Keycloak Admin Client 26.0.12 (client credentials per target).
- Product detection for community Keycloak vs Red Hat build of Keycloak (RHBK).
- Capability detection driven by server feature flags (not rigid version equals).
- Sensitive data filter that redacts secrets, passwords, and tokens from tool output.
- Structured audit logging with `targetId` and Micrometer / OpenTelemetry hooks.
- Assessment engine foundations: Evidence → Rule → Finding → Scoring (all stamped with `targetId`).
- Sample YAML rules for OpenShift HA (`KC-OCP-HA-001`) and common security/production stubs.
- Environment discovery skeleton (`UNKNOWN` when cluster discovery is disabled).
- Local demo stack: Keycloak 26.7.1 via `dev/compose.yaml`, optional second instance
  (`--profile multi-target`) for isolation demos (`lab-keycloak-a` / `lab-keycloak-b`),
  plus **PostgreSQL** for platform persistence.
- **Operations Platform backend**: Flyway migrations, assessment/health/audit/snapshot
  persistence, REST `/api/v1` (fleet, overview, history, semantic metrics), `MetricsProvider`
  abstraction, SSE events stub, UI architecture docs.
- OpenShift and Kubernetes deploy manifests (read-only RBAC, hardened securityContext).
- Unit / persistence / REST tests including multi-target isolation and secret-leakage checks.
- Documentation under `docs/` including persistence, REST API, snapshots, audit, UI concepts.

### Security

- Default `mcp.read-only=true` — no write MCP tools registered in 0.1.0.
- SSRF protection: tools accept only registered `targetId`, never arbitrary URLs.
- Client secrets and `credentialRef` are never returned to the LLM or stored as plaintext in DB.
- Audit mode default `SANITIZED`; `SensitiveDataFilter` applied before persistence and responses.
- OpenShift Secret template uses placeholders only.


### Compatibility notes

- Verified against community Keycloak container `quay.io/keycloak/keycloak:26.7.1`
  with dual-target lab (`mcp-demo` vs `company-b`).
- RHBK 26.6.x is supported by design (same Admin API surface) but **not auto-tested** in CI
  because images require authenticated access to `registry.redhat.io`.
