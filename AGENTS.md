# AGENTS.md — AI coding workflow

**Source of truth:** Git, code, tests, and repository documentation.  
**Do not** rely on conversation history. **Do not** treat prompts as specifications.

## Start here

1. Read [`docs/project-state.md`](docs/project-state.md)
2. Read [`docs/milestones/README.md`](docs/milestones/README.md) and identify the **CURRENT** / next milestone
3. Read that milestone specification
4. Read related [`docs/requirements/`](docs/requirements/) and [`docs/architecture/`](docs/architecture/)
5. Run `mvn clean verify` and record the baseline before coding

For documentation-only changes, inspect the implementation and validate the changed documentation; do not present earlier test results as a new run. Use Java 21 through the user's jenv selection per command, without changing the global Java version.

More policy: [`docs/development/ai-assisted-development.md`](docs/development/ai-assisted-development.md)

Use the executable H1/D1/D2/D3/AGT/portable/P milestones in that index; historical 0.x "next" sections are not the current queue. Milestone acceptance requires evidence, not only implemented files. Documentation map: [docs/README.md](docs/README.md); development/release version decisions: [release-versioning.md](docs/development/release-versioning.md).

## Architecture invariants (summary)

- Multi-target: every op uses `targetId` — never LLM-supplied system URLs or credentials
- Restricted configuration reads resolve target/realm/internal resource IDs and fields from an authorized `scopeId`; keep role + verified client + channel checks before target/provider access. Never call legacy READ services as a restricted fallback. Scoped grants do not narrow existing broad target roles
- MCP and REST share application services
- No raw Admin REST / kubectl / oc / PromQL tools
- Assessments are deterministic (Evidence → Rules → Findings); LLM does not decide PASS/FAIL
- PostgreSQL is not a TSDB
- Never return, persist, or log secrets; preserve target isolation
- Metadata redaction is a lossy report/history projection, never rule input or desired/applied change state; keep these boundaries separate
- Prefer Admin REST; use capability detection for version-specific features
- Operational tools are read-only by default
- Keep AGT1 report and AGT2 scoped-client contracts separately versioned. The AGT2 host must bind each call to a non-reused authenticated context generation, invalidate visible/pending data on changes and supervise cancellation; offline fixtures do not establish real client/model acceptance
- Infrastructure uses an explicit approved connection and installation identity, never ambient kubeconfig or first-match selection
- Operator direction (ADR 0012 / OP1): a separate controller manages only the Operations installation, never observed Keycloak workloads or implicit target grants. Draft installation examples are not deployable CRDs. Preserve one owner per configuration field, existing read-only/BIND semantics and single-replica limits until separately accepted
- Registry preflight (ADR 0013 / ONB1, contract 0.1.0) is default-closed REST only: exact verified issuer/subject/role/client before body parsing or registry reads. Candidate URLs are inert bounded draft data, never outbound authority. No credential resolution, target write, audit write, grants, MCP tool or global read-only/BIND change; locally valid is not registered or connectivity-verified
- Registry ownership (ADR 0014 / V11): legacy rows stay LEGACY_UNCLASSIFIED; only trusted bootstrap creates CONFIGURATION ownership. Never infer/adopt ownership by matching ID/content. Reconcile effective changes with row locking, ORM registry revision and mandatory atomic SYSTEM audit; no-op is write/audit-free. Database/composite never falls back to configuration. Populated upgrades/adoption and mixed-version rollback need a separate reviewed procedure; binding revision and global read-only/BIND remain distinct
- Candidate discovery requires READ + DISCOVER; confirmation additionally requires BIND and respects global read-only. Binding, run consumption and mandatory success audit share a transaction; no cluster write is implied

## Keep continuity current — every completed slice

Before handing off, reconcile these together:

1. `docs/project-state.md`: delivered work, exact source/working-tree status, recorded validation, limits and next step.
2. Relevant `docs/architecture/` documents: implemented components, interfaces, persistence, trust boundaries and planned versus delivered behavior.
3. `docs/milestones/README.md` and the affected specification: actual partial/completed acceptance; passing local tests does not complete a live-integration gate.
4. `CHANGELOG.md` under Unreleased: additions, compatibility changes, security and migrations.
5. Artifact versions and references: inspect `pom.xml`, application/MCP/OpenAPI metadata, `ui/package.json` and `ui/package-lock.json` root metadata; explicitly record changed, unchanged or pending versions. Milestone IDs, migrations and report schema versions are not product release versions. Do not infer release/tag authorization from a development update.

Preserve dated validation ledgers as historical evidence; link subsequent deliveries instead of rewriting old results as current. Update this AGENTS.md when workflow/invariants change, not as a duplicate release log. Record unfinished bookkeeping as a visible handoff item.

## Local environment boundaries

Do not use an unrelated cluster, ambient kubeconfig or default runtime context. Real RHBK/OpenShift acceptance requires the explicitly approved lab. Tests must use identifiable transient resources, clean up resources they own and preserve reusable volumes/images; never globally prune. Report actual cleanup and skipped integrations.

The identity/installation/configuration runners pin the explicitly selected local Podman connection, use per-run ownership labels and bounded process groups, and preserve their lock when cleanup cannot be verified. Never break that lock or restart a VM automatically to make a test pass; inspect the exact run and obtain any additional recovery authority first. A browser walkthrough after a scripted failure is diagnostic only and must preserve the failed result.

## Validation

```bash
mvn clean verify
```

Do not claim an integration was tested unless it actually ran. Opt-in ITs: `RUN_KEYCLOAK_IT`, `RUN_RHBK_IT`, `RUN_PROMETHEUS_IT`.

## Git

Do **not** commit or push without explicit authorization. Do not tag `*-SNAPSHOT` releases.

## Final report (when finishing a milestone)

Keep it short: baseline, features, main files, verify result, test counts, ITs actually run, limitations, next milestone.
