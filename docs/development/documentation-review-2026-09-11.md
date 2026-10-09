# Documentation review and milestone planning — 2026-09-11

## Scope and source

Repository: `keycloak-operations`; inspected HEAD `572cb7b`, branch `feature/0.8.1-client-lifecycle`, with extensive preexisting uncommitted/untracked implementation. This turn changes Markdown documentation/planning only; existing source, tests, configuration, package versions and migrations were preserved. No commit, push, rebase, tag, release, deployment or runtime cleanup was performed.

Inventory covered all **96 existing project Markdown documents** and **20 newly added documents**, including this ledger: **116 documents**. Automated checks read the complete Markdown corpus for local links/headings and requirement references; source-grounded editorial review focused on active guides, architecture, requirements, delivery status and historical-versus-current claims. This is not a fresh exhaustive code/security or dependency-advisory audit.

Included: repository root, `docs/` (topics, architecture, requirements, ADRs, UI concepts, development ledgers and milestones), UI README, integration-test README and OpenShift README. Temporary prompts, dependencies, generated build output, Git internals, credentials and synchronized ChatGPT reference files are excluded. Historical validation bodies and released changelog entries were not rewritten as current test evidence.

## Corrections and decisions

| Area | Correction / source checked |
|---|---|
| Authentication | REST/UI/development guides now distinguish packaged fail-closed defaults from explicit open lab. OIDC/PKCE is implemented, full browser acceptance is open; checked application properties, AuthProvider/oidc and target authorization |
| Permissions | APPROVE versus WRITE, explicit READ/DISCOVER/BIND, global read-only and trusted actor documented; checked ChangeManagementService and InstallationOnboardingService |
| Infrastructure | Removed documented implicit in-cluster/global fallback; static credentials, explicit namespace, exact root and scoped networking described; checked credential provider, explicit config, discovery/factory and inventory |
| Persistence/evidence | V1–V10, managed confirmation audit, real infrastructure snapshots, legacy hash/diff limits and incomplete-data score availability distinguished; checked migrations, SnapshotService, EnvironmentChangeService and AssessmentResult |
| Compatibility | Removed unsupported illustrative Kubernetes/OpenShift ranges and undated RHBK "latest" wording; distinguished historical local RHBK fixture checks from current skipped ITs and unverified real cluster |
| Navigation | Added documentation map, missing architecture/ADR index entries, implemented UI/installation routes and explicit future comparison behavior |
| Delivery | Added 15 executable specifications with dependencies, functional owners, ordered scope, requirement traceability, tests/exit gates and exclusions. H1/D1 are current; D3 split into D3E/D3R; AGT1 spans existing agent gates; portable tracks do not silently block the presentation |
| Requirements/decisions | Added 12 roadmap-extension requirements without renumbering old IDs; strengthened target authorization wording to match implemented fail-closed controls; ADR 0010 records accepted explicit identity direction |
| Historical context | Historical 0.1–0.8 milestones retain their content with a current-status notice; 0.8.1 realm work remains deferred to P2. Dated test ledgers remain evidence for their own source only |
| Changelog/versions | Unreleased now consolidates identity, trust, connection, binding/networking/confirmation and documentation deliveries. Artifact versions deliberately unchanged for this documentation-only turn; H1/D1 tracks development-version selection/alignment |

## Residual implementation work — not fixed by editing documentation

| Follow-up | Milestone / boundary |
|---|---|
| Browser/IdP end-to-end, token lifecycle, target-filtered events and mock-cluster confirmation | D1; local mocks cannot close D2 |
| Provider-error sanitization and confidence semantics | H1/D2 review: EnvironmentDiscovery still appends exception messages in a failure path and can preserve a platform classification when version lookup is unavailable. Validate output/coverage rather than claiming a universal safe-error/complete-discovery result |
| Snapshot digest/diff semantics | D3E/P1: legacy normalization drops UID and generic map differences do not distinguish failed collection from deletion |
| Populated upgrades, independent report retention/replay, bootstrap invalidation audit and managed-binding reconciliation | D3E/ONB1/P4; fresh-database tests do not prove customer upgrade safety |
| Real RHBK/OpenShift topology, RBAC, network/certificates and per-metric semantics | D2; only the explicitly approved lab |
| New target/connection registration, container and host/offline collectors | ONB1/PORT1/PORT2; not implemented |
| IAM sources/privacy/denominators, continuous alerts, durable writes and isolated SPIs | P1/P2/P3; no new capability or external authority created by this review |

## Validation

Final checks passed: **116 documents**, **504 local link destinations** (including two heading-fragment checks), **138 uniquely defined requirement IDs** and **15 milestone specifications** with status, owner, roadmap/requirement references and unchecked acceptance criteria. No missing local destinations, invalid heading fragments, undefined referenced requirements (including shorthand ranges), duplicate requirement IDs or unclosed fenced blocks were found. `git diff --check` passed. External URLs were retained as references, not checked for availability or newly used as compatibility evidence. Markdown syntax checks are not visual/browser rendering acceptance.

Application tests/builds were **not rerun**; no Java/global-jenv change, container, image, volume or cluster access was needed. Latest recorded implementation results remain 358 backend and 94 UI passes, both builds successful and nine opt-in integrations skipped in the [installation ledger](installation-onboarding-2026-09-11.md). This review does not refresh those results.

## Handoff

Use the [documentation map](../README.md) and [milestone index](../milestones/README.md). Next work is D1, with H1 closure and a recorded development-version decision before the next functional change. Backend remains `0.8.0-SNAPSHOT`; UI/lockfile/application/MCP/OpenAPI metadata remains `0.8.0`. Follow [versioning](release-versioning.md). Presentation date/year and duration still need organizer confirmation; existing planning dates are unchanged and not delivery guarantees.
