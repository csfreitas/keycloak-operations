# Project state (HEAD)

Recovery context updated **2026-09-04**. Git, code, tests and repository documentation are authoritative; conversation history is not a specification. Derive current HEAD and inspect the working tree before continuing.

## Product and direction

**Keycloak / RHBK Operations Platform** (keycloak-operations-mcp + ui/) provides registered multi-target discovery, configuration/security assessments, health checks, semantic runtime metrics and evidence-based explanations through shared MCP/REST services and a Fleet UI. Deterministic rules and policies decide findings and permitted changes; AI explains them.

Repo: https://github.com/csfreitas/keycloak-operations

The immediate goal is a trustworthy **read-only** demonstration for **15 January 2027** (year inferred from the supplied date; duration not confirmed). Local validation comes first, followed by a dedicated real RHBK/OpenShift environment. Community Keycloak, a configured product label or a local container is not evidence of RHBK/OpenShift HA.

Delivery order: **H1 trust hardening → D1 local workflow → D2 RHBK/OpenShift → D3 trustworthy documents → D4/D5 pilots, rehearsal and readiness**. The [complete roadmap](roadmap.md) defines dates, dependencies, owners and acceptance. The [presentation specification](milestones/2027-01-demo-readiness.md) maps the submitted abstract to go/no-go checks.

## Version and milestones

Artifact remains **0.8.0-SNAPSHOT** (pom.xml / ui/package.json). This work does not publish a release or declare production readiness.

| Area | Status |
|---|---|
| Milestones 0.1–0.8 | Historical completed foundations; not production certification |
| 0.8.1 administration | Client Slices 1–3 implemented; realm Slice 4 explicitly deferred to P2; milestone not automatically complete |
| Current delivery | H1 corrective implementation locally validated in working tree; D1/D2 acceptance still open |
| 0.8.2 reporting/onboarding | On-demand report foundation implemented; full history/onboarding/replay incomplete |
| 0.8.3 authorization/governance | Essential identity/target controls brought forward into H1; full governance remains planned |
| Version index | [milestones/README.md](milestones/README.md) |

Historical milestone commits: 0.7 610e444, 0.6.1 9ebadc9, 0.6 81eff56, 0.5 c0d00a3, 0.4 4d01a9a. These are historical references, not current HEAD.

## Implemented foundation

- Modular Quarkus backend, React UI, shared REST /api/v1 and MCP services, target registry/credential references, PostgreSQL history and Flyway V1–V8.
- Stable Keycloak Admin REST reads, OpenShift/Kubernetes inventory collectors, deterministic rules/profiles, health engine, semantic Prometheus queries and target isolation.
- Controlled client administration: URL/origin sets; typed PKCE/flows/public-confidential settings; creation and enable/disable. These are separate from the principal read-only presentation flow.
- On-demand operations report combines snapshots, health, assessments/findings and optional metrics into structured JSON and deterministic Markdown. It is not independently persisted as a report artifact.

## H1 changes applied

**Identity and isolation.** Packaged applications default to authenticated, fail-closed access and loopback listeners. Explicit local-lab, dev and test profiles support unauthenticated local use. OIDC protects REST, MCP and SSE; audience and intended role-claim path are configured. Exact role → target → permission grants restrict services, target/fleet/MCP discovery, audit counts/pages and SSE events. APPROVE is distinct from WRITE; audit/approval provenance uses trusted identity instead of caller actor strings. Raw MCP traffic logging is disabled. See [identity model](identity-model.md).

**Evidence and conclusions.** Realm rules evaluate scoped evidence. Duplicate unscoped keys do not silently resolve to the first realm. Denials, truncation and missing metadata remain explicit gaps; permitted realm/client reads continue when server-info metadata is unavailable. Observed product/version is distinct from configured type; keycloak.version.raw is an API observation when available, not a copied fixture label. Partial/unavailable assessments have no favorable overall score in UI/Markdown/compact MCP; legacy numeric fields remain with an explicit scoreAvailable contract.

**Change integrity.** Legacy scalar updates no longer accept PKCE; the typed semantic security path owns it. Idempotency is bound to normalized intent; approval/apply validate integrity, target context and policy. V8 preserves historical plans but requires pre-context pending plans to be replanned before execution. Same-plan lifecycle changes use a database row lock. This is containment, not durable exactly-once remote execution.

**Report provenance.** Schema **1.1** identifies collection start/end, INDEPENDENT_SECTION_COLLECTIONS, packaged rule-catalog SHA-256 and retainedEvidenceReplayAvailable=false. Snapshot/health/assessment IDs are references. Neither the hash nor these IDs prove replay, runtime-rule equivalence, a signature, an atomic snapshot or correctness of every conclusion. See [reporting architecture](architecture/operations-reporting.md).

**Output safety.** JSON/Markdown remain the implemented document formats. Markdown findings include bounded entity/evidence context, escaped metadata and report-specific string redaction. These controls do not provide PDF/DOCX export, signed evidence or offline replay. Current health checks are lightweight, not the complete dependency/component health design in the roadmap.

**Deployment/validation.** OpenShift templates use an authenticated read-only assessor configuration, separate Identity A/B/database secrets and one backend replica; placeholders require provisioning/review. A genuine opt-in read-only RHBK IT replaces the flag-only placeholder. Templates and synthetic-identity HTTP tests do not establish actual OpenShift or real OIDC token validation.

## Designed, not delivered

| Capability | Boundary / plan |
|---|---|
| High-confidence documents | [Design](architecture/trustworthy-reporting.md): immutable retained evidence bundles, report history, replay, export QA and optional signatures remain future work |
| PDF/DOCX and executive documents | Planned outputs from a canonical record, not implemented/export-validated formats today |
| IAM/business observability | [Design](architecture/iam-business-observability.md): login/application indicators, DAU/WAU/MAU, MFA usage, SLO impact and alerts require source/denominator/privacy gates; runtime metrics are not that complete suite |
| SPI assurance | [Design](architecture/spi-assurance.md): artifact review and isolated harness/runtime are planned; no arbitrary SPI upload/execution/deployment endpoint |
| Durable remediation | P2: execution attempts, uncertain-outcome reconciliation, cross-plan coordination and stronger approval governance |
| Production platform | P4: compatibility matrix, scale, retention, backup/restore, independent security review and recovery acceptance |

## Validation: baseline, interim and final

The [final local evidence ledger](development/trust-hardening-validation-2026-09-04.md) records the tested source manifest, failures corrected during validation, results and cleanup. It does not certify event or production readiness.

| Check | Recorded result / scope |
|---|---|
| Before H1: Java 21 via jenv, mvn clean verify | **205 passed**, 0 failed; **8 opt-in ITs skipped** |
| Before H1: UI tests/build | **51 passed**; build **SUCCESS** |
| Interim H1: backend verify | **251 passed**, 0 failed; **8 opt-in ITs skipped**; later RHBK/metadata corrections require revalidation |
| Interim H1: UI tests/build | **64 passed**; build **SUCCESS** |
| Final UI tests/build | **69 passed**; build **SUCCESS**, reported by coordinator after final UI corrections |
| Synthetic Identity A boundaries | REST/MCP/SSE denial and role/target-scoped REST + actual MCP HTTP calls exercised; **not real IdP cryptographic/token validation** |
| Local runtime | Named disposable PostgreSQL, Community Keycloak26.7.1, Prometheus and cached RHBK; live tests completed and resources removed |
| RHBK read-only IT | **2 passed** on fixture26.6.3.redhat-00002; permitted reads, partial report and metadata uncertainty preserved; no RHBK writes |
| Final integrated backend | **280 passed**, 0 failures/errors; **7 selected live ITs passed**, 0 failures/errors/skips; clean verify/build SUCCESS at2026-09-04 20:07:39 -03:00 |
| Packaged MCP/REST | **PASS**; report1.1, PARTIAL/healthUNKNOWN/scoreAvailable=false, 4 evaluated+8 not evaluated; semantic metrics collected; unknown-target denial |
| Final resource inventory | **0 containers, 0 volumes**; validation network removed; 4 preexisting reusable images and default podman network preserved |

Previous Slice 3 validation exercised four controlled-write cases on disposable Community Keycloak 26.7.1 and cleaned up its fixtures. The changed H1 path was separately rerun with the four Community controlled-write cases in the final seven ITs. RHBK writes, actual OpenShift HA, real IdP authentication and Web Origin + semantics remain **NOT VERIFIED**.

## Remaining safety/readiness limits

- Grants are target-level, not realm/client ACLs. Separate APPROVE/WRITE permissions do not prove human presence or two humans; MCP approval remains available to an appropriately authorized principal.
- Remote mutation is not atomic with PostgreSQL. Crashes/timeouts can leave uncertain outcomes; row locking coordinates one plan, not every plan or external writer touching a resource. Production writes remain gated behind P2.
- SSE/MCP state is in-process. SSE grants are captured at subscription and refreshed through bounded reconnect; immediate revocation and multi-replica fan-out are not implemented.
- Completeness is a bounded source/rule heuristic, not statistical confidence or a universal inventory denominator. Coverage is limited to implemented rules/collectors and accessible sources.
- Retained-evidence replay, independent report history, signing, retention enforcement and planned document/IAM/SPI capabilities remain incomplete.
- VM/Docker inventory collectors are absent. Local containers do not establish OpenShift topology, failover or cluster-RBAC compatibility.
- Read-only credentials can legitimately lack server-info metadata. Record unknown product/version or partial evidence; do not elevate privileges or fabricate observations to improve a demo.

## Next operator/agent

1. Read [AGENTS.md](../AGENTS.md), [roadmap](roadmap.md), current milestone and related requirements/architecture.
2. Inspect Git and the ledger; the tested working tree is uncommitted and the source subset digest is recorded.
3. Review the locally validated H1 diff, then follow D1/D2 identity/workflow and real OpenShift gates. Do not resume broad realm administration ahead of them.
4. Preserve user changes. Do not commit, push, rebase, release, publish or provision external environments without corresponding authorization.

Earlier procedure remains a historical handoff: [0.8.1 local validation](development/local-validation-0.8.1-operations-report.md).
