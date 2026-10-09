# Documentation map

Updated 2026-10-09. Start with [project state](project-state.md) for actual implementation/validation and [executable milestones](milestones/README.md) for next work. Design intent, local test results and real-environment acceptance are different things.

## Project and delivery

| Document | Purpose |
|---|---|
| [Project state](project-state.md) | Current working-tree deliveries, limits and next step |
| [Roadmap](roadmap.md) | Product objectives, presentation gates and later tracks |
| [Milestones](milestones/README.md) | Executable specifications and historical mapping |
| [Requirements](requirements/README.md) | Stable functional/security/non-functional/compatibility and roadmap-extension IDs |
| [Architecture](architecture/README.md) | Components, trust boundaries and planned evolution |
| [ADRs](adr/README.md) | Accepted design rationale, including explicit installation identity |
| [Changelog](../CHANGELOG.md) / [version workflow](development/release-versioning.md) | Unreleased changes versus artifact/migration/report versions |
| [AGENTS.md](../AGENTS.md) / [contributing](../CONTRIBUTING.md) | Development workflow and evidence-backed handoff |

## Operator and integration guides

| Area | Guides |
|---|---|
| Local setup | [Development](development.md), [authenticated browser and installation labs](../dev/identity-lab/README.md), [local/cloud handoff](development/local-cloud-workflow.md), [integration tests](../integration-tests/README.md), [UI setup](../ui/README.md) |
| Identity and credentials | [Two identities](identity-model.md), [security](architecture/security.md), [infrastructure authentication](infrastructure-authentication.md) |
| API and agent usage | [REST](rest-api.md), [MCP tools](tools.md), [example questions](examples.md), [reference-agent milestone](milestones/agt1-reference-agent.md) |
| Discovery and inventory | [Environment discovery](environment-discovery.md), [inventory](infrastructure-inventory.md), [portable design](architecture/portable-environment-discovery.md) |
| Administrative onboarding | [Preflight contract 0.1.0](registry-preflight.md), [registry ownership/V11](architecture/registry-ownership.md), [ADR 0013](adr/0013-registry-preflight-before-registration.md), [ADR 0014](adr/0014-registry-ownership-before-managed-writes.md), [ONB1](milestones/onb1-environment-registry.md); local draft checks and audited configuration ownership, not public registration or destination approval |
| Assessment | [Engine](architecture/assessment-engine.md), [profiles](assessment-profiles.md), [rules](rule-catalog.md), [evidence](evidence-catalog.md), [scoring](scoring.md), [planned explainable rating](architecture/operational-rating.md), [rule development](rule-development.md) |
| Health and metrics | [Health](health-check.md), [catalog](metrics-catalog.md), [performance assessment](performance-assessment.md), [SLOs](performance-slo.md), [observability](observability-integration.md), [Prometheus](prometheus-integration.md), [OpenShift monitoring](openshift-monitoring.md) |
| Persistence and history | [Persistence](architecture/persistence.md), [schema](database-schema.md), [snapshots](snapshots.md), [audit](audit.md) |
| Reporting | [Current on-demand report](architecture/operations-reporting.md), [planned trustworthy documents](architecture/trustworthy-reporting.md), [planned IAM indicators](architecture/iam-business-observability.md) |
| UI | [Architecture](ui-architecture.md), conceptual [fleet](ui/fleet-dashboard.md), [overview](ui/target-overview.md), [assessment](ui/assessment.md), [infrastructure](ui/infrastructure.md), [history](ui/history.md) |
| Deployment and compatibility | [OpenShift templates](../deploy/openshift/README.md), [tested scope](compatibility.md); templates and mock APIs do not establish live compatibility |
| Planned installation Operator | [Architecture](architecture/operator-managed-platform.md), [draft contract 0.1](architecture/operator-installation-contract.md), [ADR 0012](adr/0012-operator-managed-portable-platform.md), [OP1](milestones/op1-operator-installation.md); no controller/CRD/bundle yet |
| Future execution | [Controlled administration](architecture/controlled-administration.md), [SPI design](architecture/spi-assurance.md); production governance and isolated execution remain gated |
| Operator console + assistant | [Access-aware assistance](architecture/access-aware-operations-assistant.md), [restricted configuration contract](configuration-reads.md), [reference client 0.1.0](../dev/access-aware-client/README.md), [AGT2](milestones/agt2-access-aware-assistance.md), [ADR 0011](adr/0011-access-aware-dual-interface.md); server and bounded client exercised in the authenticated local lab; general host deployment and broader domains pending |

## Evidence and historical material

- Current integration: [consolidated source checkpoint](development/repository-integration-2026-10-09.md); fresh Java/Node/UI regression, targeted npm dependency remediation and explicit main-integration authorization. Source publication is not release, live-cluster or populated-upgrade acceptance.
- Current implementation: [ONB1 ownership ledger](development/onb1-ownership-2026-10-09.md); additive V11, conservative legacy classification, ORM revisions, no-op reconciliation and atomic system audit. Migration/concurrency/rollback tested only with disposable data; governed adoption and public registration remain open.
- Previous implementation: [ONB1 preflight ledger](development/onb1-preflight-2026-10-09.md); default-closed REST, local draft validation and negative unit/synthetic HTTP tests. Public registration and audited administrator bootstrap remain open; local Podman configuration repair was separately authorized.
- Previous documentation slice: [Operator design ledger](development/operator-architecture-2026-10-09.md); draft contract/ownership, planned OP1, no runtime/cluster validation or version change in that slice. H1/D1 review and reproduction remain release gates.
- Latest real OIDC/provider validation (historical, 22 September): [reference client with real local operators](development/agt2-client-live-2026-09-22.md); fixed loopback host and two real OIDC/MCP identities, 72 checks passed twice, no AI. Consolidated release/H1 review and independent operator reproduction remain open. AGT2 stays partial.
- Previous delivery: [offline reference client 0.1.0](development/agt2-client-2026-09-21.md), versioned host/answer contract and deterministic tests, preserved as separate synthetic evidence.
- Previous validation: [two restricted authenticated operators](development/agt2-authenticated-operators-2026-09-19.md) exercise actual local Community OIDC/provider, console and REST/MCP boundaries. No model, RHBK/OpenShift or actual resource-recreation acceptance.
- Previous implementation: [AGT2 configuration reads](configuration-reads.md) and [synthetic evidence](development/agt2-configuration-reads-2026-09-19.md) add a default-closed realm/client Boolean channel shared by console/REST/MCP. Broad legacy grants and report-only AGT1 are unchanged.
- Previous direction/documentation slice: [AGT2 evidence](development/agt2-direction-2026-09-19.md) records the initial docs-only addition, preserved as historical evidence.
- Previous backlog/test-data slice: [SecOps/IAM catalogue](development/secops-iam-scenarios.md), [39 synthetic cases](../dev/secops-scenarios/README.md) and [validation ledger](development/secops-catalogue-2026-09-19.md). Read-only policy/investigation goes to P1; temporary-user access and containment remain gated P2. Catalogue integrity is not implemented-feature or model acceptance; AGT1 permissions and the delivery queue are unchanged.
- Previous local slice: [AGT1 contract alignment](development/agt1-contract-alignment-2026-09-19.md), with [profile 0.2.1](../dev/reference-agent/README.md), unchanged explanation limits published consistently, offline regressions and event inspection. The three historical model responses failed structural validation and remain unchanged; no new model run occurred. Revised-profile evaluation, authenticated client integration and full milestone acceptance remain open.
- Previous local validation: [real-browser identity negatives](development/d1-browser-negatives-2026-09-19.md), with three native-401 signed-token failures and a valid 200/scoped-Fleet control. The [fixture guide](../dev/ui-browser-negatives/README.md) describes fixed-scope observation and controlled expiry delay. The D1 local browser criterion is met together with prior session evidence; H1/D1 acceptance and independent operator reproduction remain open.
- Previous UI slice: [change-navigation isolation](development/d1-change-navigation-2026-09-19.md), with late-response/action regressions, response-identity checks, localized control improvements and a [synthetic browser fixture](../dev/ui-change-navigation/README.md). H1/D1 remain open; no real-write or OIDC acceptance is implied.
- Previous UI slice: [browser-session transport](development/d1-browser-session-2026-09-19.md); latest backend slice: [read metadata/environment authorization](development/h1-read-metadata-2026-09-19.md). Their ledgers retain separate dated evidence and limits.
- Previous backend slice: [controlled-change/audit metadata](development/h1-change-metadata-2026-09-19.md). Admission rejects unsafe intent/observations without substituting applied values; historical change and audit output is filtered without rewriting state, fingerprints or canonical identities. Scoped errors redact recognizable credentials; change-MCP errors discard cause chains. H1/D1 acceptance remains separately gated.

- Previous backend slice: [metadata trust projection](development/h1-metadata-trust-2026-09-19.md). Explicit post-evaluation filtering covers report/snapshot/assessment outputs and historical diffs; Markdown renders sanitized structured facts. Historical bytes/hashes and operational change state remain separate. The ledger records tests, local runtime cleanup and remaining legacy/agent/browser limits; H1/D1 remain open.

- Previous backend slice: [observed cluster capabilities and source coverage](development/h1-capability-coverage-2026-09-19.md). Configured intent no longer drives Route/config collection; shared conservative completeness reaches assessment, snapshots/reports and infrastructure health. The ledger separates its dated tests from open broader H1/D1 gates.

- Latest local runner follow-up: [19 September validation](development/d1-runner-validation-2026-09-19.md) resolves transient process-group probe uncertainty, preserves failure/timeout semantics, validates owned fixture SQL and records the final local evidence. H1/D1 and real-cluster acceptance remain separately gated.

- Latest local recovery: [explicitly authorized Podman restart and cleanup](development/h1-admin-boundaries-recovered-2026-09-18.md). Fresh default 70 checks/JWT scan passed, but runner exit 1 records automatic-cleanup SSH EOF. Separate scoped cleanup exited 0; final containers/volumes/ports/lock were clean, reusable images retained. Prior failures are preserved; its local runner follow-up was validated on 19 September above, without establishing the original VM/SSH failure cause.

- Previous backend slice: [Admin response and diagnostic boundaries](development/h1-admin-boundaries-2026-09-18.md), with scoped token/body/list/parser validation, unsafe diagnostic admission and fixed errors, preserving partial evidence and ordinary administration. Full schema/capability reconciliation, source coverage, metadata filtering and remaining trust gates stay open. [Shared collection budgets](development/h1-compound-collection-2026-09-18.md), [inventory envelopes](development/h1-inventory-envelopes-2026-09-18.md), [scrape readiness](development/h1-scrape-readiness-2026-09-18.md) and [metrics budgets](development/h1-operation-budgets-2026-09-18.md) retain their historical results.
- Previous backend slice: [inventory/temporal hardening](development/h1-evidence-temporal-2026-09-18.md), with safe inventory warnings, partial-source propagation and validated temporal metric responses; 561 backend tests, 9 opt-in ITs skipped. See the ledger for local labs, source hashes and limits. UI/dependencies remain unchanged.
- Previous maintenance slice: [dependency remediation](development/h1-dependency-fix-2026-09-18.md), with backend/UI migrations, Node 24 LTS, zero-finding npm audits, 496 backend / 133 UI tests, actual local browser/identity/installation evidence and current source/package inventories. The original nonzero audits remain historical; no whole-platform security certification is implied.

- Latest corrective slice: [H1 provider failures and response bounds](development/h1-failure-bounds-2026-09-18.md), with 496 backend / 133 UI tests, real local metrics/default identity regressions, source manifest and cleanup. [Dependency review](development/h1-dependency-review-2026-09-18.md) retains raw audits/inventory and open remediation; it is not a clean-security certificate. H1/D1 acceptance remains open.

- Latest local installation slice: [real OIDC identity and synthetic cluster API](development/d1-installation-identity-2026-09-18.md), with separate setup permissions, scoped discovery/confirmation, negative binding checks and runner safeguards. The ledger records repeated automated runs and the browser selection/review/confirmation flow, including audit and ordinary-reader restrictions; H1/D1 acceptance remains open.
- Previous report correction: [report unknown-data projection and explicit heap policy](development/d1-report-trust-2026-09-11.md), with its own regression and local REST/MCP evidence.
- Previous report validation: [real Prometheus and authenticated REST/MCP/browser reports](development/d1-metrics-report-2026-09-11.md), two clean metrics runs, default-mode regression, source manifest and explicit remaining trust gaps.
- Latest navigation increment: [safe OIDC return paths](development/d1-return-path-2026-09-11.md), with real browser permitted/denied deep-link observations and fresh UI regression.

- Previous corrective ledger: [D1 health and missing-data corrections](development/d1-health-corrections-2026-09-11.md), including the clarified null-version cause, source manifest and that slice's regression/live evidence.

- Previous local workflow ledger: [D1 browser, reports and events](development/d1-browser-workflow-2026-09-11.md), including source manifest, two clean automated runs and open correctness findings. Previous implementation ledger: [installation confirmation](development/installation-onboarding-2026-09-11.md).
- Earlier local slices: [identity](development/local-identity-validation-2026-09-11.md), [portable foundation](development/portable-discovery-validation-2026-09-11.md), [explicit connections](development/explicit-infrastructure-connections-2026-09-11.md), [exact binding](development/exact-installation-binding-2026-09-11.md), [networking](development/installation-networking-2026-09-11.md).
- Historical live H1 evidence: [2026-09-04 validation](development/trust-hardening-validation-2026-09-04.md). Its RHBK fixture checks do not validate current-working-tree cluster behavior.
- Historical procedure: [0.8.1 Slice 1 validation](development/local-validation-0.8.1-operations-report.md); not the current full acceptance checklist.
- The historical [documentation review](development/documentation-review-2026-09-11.md) records that dated sweep and its limits.

Short legacy topic files (`architecture.md`, `assessment-engine.md`, `multi-target.md`, `persistence.md`, `security.md`) intentionally redirect to canonical architecture documents. Dated ledgers and historical 0.x milestones are retained, not rewritten into new test claims. Temporary `.agent-prompts/`, generated build output, dependency documentation and credentials are not public project documentation.
