# Trust-hardening validation ledger — 2026-09-04

**Status: LOCAL VALIDATION PASSED — H1 corrective slice tested; OpenShift, real IdP and full roadmap acceptance remain open.**

This ledger distinguishes implemented changes, completed checks, runtime preparation and unverified claims. A test in source, passing unit test, running container or configured product label alone is not end-to-end compatibility evidence.

## Scope and source state

- Product: Keycloak / RHBK Operations; artifact remains **0.8.0-SNAPSHOT**.
- Delivery: H1 corrective foundation for the [15 January 2027 presentation](../milestones/2027-01-demo-readiness.md), followed by the D1–D5 gates in the [roadmap](../roadmap.md).
- Source base observed while preparing this ledger: **fb4e292**, branch **feature/0.8.1-client-lifecycle**, with uncommitted H1 changes. The base commit alone does **not** identify all changed files.
- Java checks: Homebrew OpenJDK **21.0.10** selected with `JENV_VERSION=21 jenv exec`; Maven **3.9.12**, Node **25.6.1**, macOS ARM64. Global jenv selection unchanged.
- Tested source manifest SHA-256: **4da399669d809e13f3cb6a0102dd81bfd788d6903e4a97cd801580d28ed45771**. Method: sorted tracked/untracked non-ignored paths under src, ui/src, pom.xml, ui/package.json and ui/package-lock.json; hash each file, then hash the path/hash listing. This identifies that source subset, not every repository file or a signed release.
- No release, commit, push, merge or rebase is implied by validation.

## Completed baseline and interim checks

| Stage | Check | Result | Interpretation |
|---|---|---|---|
| Before H1 | Java 21 backend clean verify | **205 passed**, 0 failures; **8 opt-in ITs skipped** | Existing baseline, not H1/RHBK acceptance |
| Before H1 | UI tests + production build | **51 passed**; build **SUCCESS** | Existing frontend baseline |
| Interim H1 | Backend clean verify | **251 passed**, 0 failures; **8 opt-in ITs skipped** | Intermediate tree; later RHBK/metadata/report changes require final revalidation |
| Interim H1 | UI tests + production build | **64 passed**; build **SUCCESS** | Intermediate frontend |
| Final UI | UI tests + production build | **69 passed**; build **SUCCESS** | Coordinator-reported final UI verification; not a live browser/IdP rehearsal |
| Configuration review | Changed OpenShift YAML parsing and diff whitespace checks | **PASS at interim review** | Syntax/review only; no cluster admission, network or rollout proof |

Local baseline/interim logs: `/private/tmp/kcops-baseline-20260904.log` and `/private/tmp/kcops-final-20260904.log`. Despite its name, the latter is interim. Final source acceptance: `/private/tmp/kcops-final-acceptance-20260904.log`, completed **2026-09-04 20:07:39 -03:00**, **BUILD SUCCESS**, 280 unit/component tests plus 7 selected live ITs, no failures/errors/skips in those selections. UI: `/private/tmp/kcops-ui-final-20260904.log`, 69 tests and production build passed. Temporary logs are not portable published evidence or guaranteed retention; none were committed.

A first live attempt failed to start because the smoke application occupied port 8081; no integration success was claimed from it. It was stopped and the final suites passed. Packaged HTTP checking also revealed an omitted calculated score-availability property; it is now a derived record component, verified present and false for a partial live report. Final MCP smoke: `/private/tmp/kcops-mcp-contract-final-20260904.log`, SUCCESS.

## Corrective implementation and evidence boundaries

| Area | Applied implementation | Checks / residual limit |
|---|---|---|
| Identity A | Fail-closed packaged default; explicit local-lab; authenticated REST/MCP/SSE; audience/role-claim configuration | HTTP tests use synthetic framework identities. Real signature/issuer/expiry/audience/role mapping and browser login remain unverified |
| Target grants | Exact role/target/permission matching; separate APPROVE; trusted actor; discovery/fleet/audit/SSE scoping | Unit/HTTP isolation regressions include real MCP initialization/tool calls. No realm/client ACL or human-presence guarantee |
| Evidence scoping | Realm-specific evaluation; ambiguous lookup does not choose first realm | Mixed/reversed realm regressions; no universal coverage claim beyond implemented rules |
| Partial collection | Denial/truncation/missing metadata explicit; unavailable server-info does not discard permitted realm/client reads | Added metadata regressions and genuine read-only RHBK IT; final read-only ITs passed |
| Product/version | Observed values separated from configured type; raw API version separated from normalized version | Red Hat build recognition added for observed fixture; external runtime metadata is never substituted as an API observation |
| Score/UI | Incomplete/non-meaningful assessment stays inconclusive; availability contract preserved through history/views | Backend/UI/Markdown/MCP regressions; completeness is not a statistical confidence probability |
| PKCE/policy | Scalar path rejects PKCE; typed security path owns semantics | Legacy-rejection/policy regressions; four final Community live control tests passed |
| Plan integrity | Intent-bound idempotency, policy/target/integrity fingerprints, V8 old-plan replan requirement, same-plan lock | Preserves history; no durable execution attempt, cross-plan serialization, exactly-once effect or crash reconciliation |
| Report 1.1 | Collection window, independent-section mode, packaged catalog digest, replay-unavailable flag | Hash identifies packaged resources, not truth, all runtime overrides, retained evidence, signature or replay |
| Report safety | Bounded entity/evidence context, escaped metadata, report-specific deep string redaction, nullable snapshot metadata | JSON/Markdown only; output tests do not establish PDF/DOCX layout/export or end-to-end privacy against every source |
| Health | Existing lightweight operational health engine | Not the roadmap's complete dependency/component health suite or evidence of actual failover |
| OpenShift templates | Authenticated/read-only profile, explicit pod listeners, one backend replica, separate credentials and scoped examples | Require actual image, namespace, DB, IdP/audience and network choices; not deployed/accepted here |

The earlier completed identity subset contained **9** target-authorization tests, **1** SSE filter test, **5** authenticated-boundary tests and **2** audit-repository tests. They are already included in aggregate backend totals—**do not add them again**.

## Local runtime preparation

The coordinator provisioned four project-scoped runtime containers: PostgreSQL, Community Keycloak, Prometheus and RHBK. They are named/identified for this validation. The Java framework can also create its own transient platform PostgreSQL resource; final inventory must account for it separately.

| Source | Preparation evidence | What this does not prove |
|---|---|---|
| Community Keycloak | Disposable Community 26.7.1 fixture and imported demo realm | Five version/control tests passed; this is a privileged development fixture, not proof of read-only least privilege |
| RHBK image | Cached `registry.redhat.io/rhbk/keycloak-rhel9:26.6`; local image ID **e7affbc8b409**; labels version **26.6**, release **6** | Tag is mutable; local image ID is not a registry digest or signature |
| RHBK runtime | Coordinator ran `kc.sh --version` in the fixture: **26.6.3.redhat-00002**; JVM **21.0.11 Red Hat** | External fixture provenance, not proof of server-info visibility to the restricted Admin REST caller |
| RHBK architecture | AMD64 cached image on ARM64 host under emulation | Functional checks do not establish representative performance, scale or production sizing |
| RHBK Identity B | Dedicated **keycloak-mcp-readonly**, intended view/query permissions; no master-admin/manage grants | Two read-only tests passed without privilege elevation; server metadata remains unknown when not visible |
| Infrastructure | Local runtime; no OpenShift/Kubernetes target for this run | No live OpenShift discovery, HA topology, RBAC, failover or rollout acceptance |
| Identity A | Explicit local-lab in live fixture ITs; synthetic identities in HTTP authorization tests | No real OIDC cryptographic/token integration or authenticated browser rehearsal |

Only disposable fixture credentials were used in these local invocations. Real environment credentials must use a secure runtime channel, never chat, committed fixtures, report output or screenshots. The delivered sample was checked for both fixture secret values.

## Final checks

| Check | Required evidence | Current result |
|---|---|---|
| Final backend clean verify | Exact totals/errors/skips, migration/build result, source state | **280 passed**, 0 failures/errors/skips; V1–V8 and package build passed |
| Final UI tests/build | Exact totals and final source state | **69 passed / build SUCCESS**; no later UI changes |
| Community opt-in version/control | Actual version, plan/approve/apply/read-back/restore, stale-plan denial, fixture removal | **PASS** (see scope below) |
| RHBK read-only IT | Permitted reads, preserved evidence, unknown metadata, partial report/provenance and no leaked credential | **PASS** (see scope below) |
| MCP/report smoke | Actual semantic tool outputs, target/source versions and report/health/assessment/provider states | **PASS** (see scope below) |
| Final secret review | Sanitized artifacts; no secret-bearing logs published | **PASS for exercised canaries/output tests**; generated sample contains neither fixture secret; not universal DLP certification |
| Cleanup | Before/after containers, volumes, networks, images; each reusable retained resource justified | **PASS**: 0 containers, 0 volumes; validation network removed; only preexisting podman network and 4 cached images remain |

### RHBK contract

The genuine Rhbk26_6IT fixes target **lab-rhbk-readonly** and requires RUN_RHBK_IT=true, exact RHBK_URL=http://localhost:8280, RHBK_EXPECTED_VERSION=26.6.3.redhat-00002 for this observed fixture, and RHBK_CLIENT_SECRET supplied privately. Auth realm: master; application realm: mcp-demo; global target writes remain disabled.

The IT does not create clients, grant roles or change RHBK configuration. It registers metadata/persists history only in the disposable **platform database**. Server-info 403 or absent metadata can legitimately leave version unknown; invalid credentials/outages are failures. The expected-version input is never substituted for an absent API observation.

With infrastructure/metrics unconfigured, assessment/report remain partial and performance skipped. This does not imply OpenShift evidence. Invocation/resource responsibilities: [integration-tests/README.md](../../integration-tests/README.md).

## Explicitly unverified / future gates

- RHBK/OpenShift HA, namespace/cluster RBAC and OpenShift Monitoring: **D2**, not established by containers.
- Real IdP token signature/issuer/expiry/audience/roles, browser login and revoked access: **D1/D2 security acceptance pending**.
- RHBK writes, Web Origin + semantics, multi-replica execution and recovery: **NOT VERIFIED**.
- Immutable retained evidence, full runtime-rule/profile provenance, report history/replay: **D3/P1 design**, not schema 1.1 delivery.
- PDF/DOCX/executive exports and visual QA: **planned**, not executed here.
- IAM/business analytics, unique active users, actual MFA usage, outage-impact models, alerts/anomalies: **source/privacy/denominator-gated roadmap**, not a complete implemented suite.
- SPI artifacts/SBOM assessment, isolated execution harness and promotion: **P3 design**; no custom-code execution endpoint introduced.
- Human-only/two-person approval, realm/resource ACL, durable uncertain-outcome reconciliation and cross-plan/external-writer coordination: **P2**.

## Live scope, samples and cleanup

- Selected ITs: ControlledClientChangeIT **4**, KeycloakCommunity26_7IT **1**, Rhbk26_6IT **2**. All passed. Community26_6 placeholder and standalone Prometheus IT were not selected; no compatibility claim for Community26.6. Prometheus semantic collection was exercised by the packaged report smoke.
- Final packaged REST/MCP report on **Community26.7.1**: schema1.1, statusPARTIAL, healthUNKNOWN, scoreAvailable=false, 4 evaluated / 8 not evaluated, completeness33%, 1 actionable finding. This is limited profile coverage, not a comprehensive inventory percentage. Metrics provider AVAILABLE; individual metrics can still be NOT_AVAILABLE.
- Final sample collection window: **2026-09-04T23:08:11.191008Z–23:08:12.325100Z**. Packaged catalog SHA-256 **37438e96b35455964755277d009eda4a343b59080b65d4cce9535e3fead5681a**.
- Sanitized Markdown and JSON samples were saved outside the Git repository in the current task's relatorios-validacao directory. They are explicitly Community laboratory samples, not RHBK/OpenShift reports or production assessments. History IDs no longer resolve after disposable DB cleanup; no retained-evidence replay is claimed.
- Removed exactly kcops-validation-keycloak, kcops-validation-rhbk, kcops-validation-postgres, kcops-validation-prometheus and keycloak-operations-validation_default. Framework test DB containers self-cleaned; final Podman inventory has no containers or volumes.
- Temporary fixture databases were discarded and are not recoverable; they can be recreated from the lab fixture. Cached images retained for reuse: Community26.7.1, PostgreSQL16, RHBK26.6, Prometheus2.55.1. No global prune, new image build, commit, push, release or external deployment.

## Coordinator completion checklist

- [x] Replace pending rows with observed final totals/versions/PASS/FAIL/SKIPPED results; avoid double-counting subsets.
- [x] Record source revision/working-tree state covered, and revalidate subsequent edits.
- [x] Record RHBK observed versus configured product/version and metadata limitations.
- [x] Deliver only sanitized samples; raw diagnostic logs stay temporary and unpublished, without a retention guarantee.
- [x] Complete scoped cleanup; state what was removed and what remains reusable. No global prune.
- [x] Update project-state with identical final outcomes; remove final-result TODOs only when established.
- [x] Keep January go/no-go open wherever RHBK/OpenShift/IdP/rehearsal evidence is missing.

This is acceptance of the recorded local corrective slice only—not event readiness, complete health coverage, security certification or authorization to publish/deploy.
