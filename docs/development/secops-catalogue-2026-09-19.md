# SecOps/IAM catalogue slice — 2026-09-19

Status: **BACKLOG AND SYNTHETIC DATA DELIVERED; PRODUCT SCENARIOS NOT IMPLEMENTED**.

## Authorized scope and delivered artifacts

The operator authorized the proposed next step: record the three user journeys and
prepare synthetic acceptance cases. This is not authorization for new model calls,
real user mutations, event ingestion, an external geolocation provider or a cluster.

- [Catalogue](secops-iam-scenarios.md): IAM-06 policy audit and SECOPS-01 investigation
  under P1; HLP-01 temporary access and SECOPS-02 separate containment under P2.
- [Fixtures](../../dev/secops-scenarios/README.md): **39 cases** (13 password age,
  11 investigation, 15 temporary-access/containment) with fixed UTC time, synthetic
  scope, explicit unvalidated product assumptions, reasons and evidence pointers.
- Four future requirements: FR-IAM-003/004 and FR-GOV-002/003. P1/P2 acceptance,
  architecture, roadmap, context, documentation map and Unreleased are reconciled.
- Dependency-free Node catalogue tests and a separate step in the existing offline
  CI job. These check authored data integrity, not a product implementation or an
  evaluator returning the authored answers. No server tool or permission added.

## Fresh validation

Before test-code changes, Java **21.0.10** was selected through jenv per command:

```sh
env -u RUN_KEYCLOAK_IT -u RUN_RHBK_IT -u RUN_PROMETHEUS_IT \
  JENV_VERSION=21 CONTAINER_CONNECTION=podman-machine-default-root \
  jenv exec mvn clean verify
```

Backend baseline: **1705 passed, 0 failures/errors; 9 opt-in integration tests
skipped**, BUILD SUCCESS, exit 0. Backend implementation remained unchanged after
this baseline. This is not real Keycloak/RHBK/Prometheus integration evidence.

Existing reference-profile tests: **115 passed, 0 failures/skips**, including exact
contract bounds, rejected historical model responses and event-log classifications.
No new model response, authenticated MCP integration or browser/UI run occurred.

Catalogue checks: **52 passed, 0 failures/skips** — 39 per-case integrity tests and
13 metadata/consistency checks. They cover IDs/requirements, strict expected shape,
non-authorizing outcomes, resolvable evidence paths, exact time arithmetic,
duplicates, source/scope/permission distinctions and approval/recovery examples.
This count does not mean 39 product behaviors were implemented or executed.
CI YAML was parsed locally; GitHub Actions was not run. Independent
agent-assisted design review found no actionable contradiction; it is not human
operator acceptance or certification of the planned capabilities.

Offline documentation validation: **149 Markdown documents, 1008 local links,
147 requirement definitions and 16/16 milestone structures**, zero errors/warnings.
Six parser self-check groups and `git diff --check` pass. The checker does not
certify external-source content or product acceptance; the two linked upstream
documentation sections were consulted separately for their pinned version.

The [evidence manifest](evidence/secops-catalogue-2026-09-19.json) records **631
implementation/test/deployment inputs**, aggregate SHA-256
`5d12d13d3523a4c182767f01cc6847c54ce9cb13edf4e69c718784bdd4440e04`.
Against the previous 626-input manifest, only CI changes and five fixture/guide/test
files are added; no input is removed. Backend/UI/reference-profile source is unchanged.
All 29 historical model-trial artifact hashes still match; no response was repaired.
Documentation is tracked separately from that source digest, not represented as
covered by it. Logs and test report hashes provide provenance, not authenticity.

Logs: `/private/tmp/kcops-secops-catalog-baseline-20260919.log`,
`/private/tmp/kcops-secops-profile-20260919.log`,
`/private/tmp/kcops-secops-catalogue-20260919.log` and
`/private/tmp/kcops-secops-documentation-20260919.log`. Temporary logs can be removed by OS
cleanup; the retained manifest records summaries/hashes, not a full raw-log archive.

## Environment and versions

The initial sandboxed Podman read failed with `operation not permitted`; approved
reads of the already selected local connection succeeded. No VM restart, connection
switch, cleanup workaround or unrelated cluster access occurred. Existing test
lifecycle used named/labelled PostgreSQL resources with memory-backed data and
automatic removal. Independent before/after inventories confirm **zero containers
and zero volumes**, with four reusable image IDs unchanged:

| Reusable image | ID |
|---|---|
| Keycloak 26.7.1 | `cc689d358fe6` |
| PostgreSQL 16 | `02ad0fee02ae` |
| RHBK 26.6 | `e7affbc8b409` |
| Prometheus v2.55.1 | `f59c592ea6d9` |

No global prune or persistent test volume. Node checks create no runtime resources.
Global Java selection and runtime configuration were not changed.

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
reference profile **0.2.1**, report **1.1**, findingDetails **1.0**, Flyway **V1–V10**,
dependencies and grants remain unchanged. Private fixture `catalogVersion: 1`
does not change a public schema or product version. AGENTS.md invariants are unchanged.
HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; pre-existing dirty work is preserved, with no
commit/push/rebase/tag. Previous dated evidence and raw model responses are preserved.

## Limits and next step

The minimal fixtures do not yet cover every acceptance condition. Future tests must
exercise actual services, exact-version provider/membership semantics, authorization,
concurrency, expiration/recovery and transport behavior; passing the catalogue cannot
close those gates. No compatibility, legal compliance, runtime detection, human
approval or model-safety claim follows from these data.

P1/P2 remain PLANNED and the H1/D1/AGT1 priority is unchanged. Within this backlog,
the next bounded task is IAM-06 read-only source/semantics feasibility, before its
deterministic evaluator and approved disposable product/version validation. New
AGT1 model evaluation needs its separately scoped response/disclosure authorization;
the previously exhausted three-response budget is not renewed by this approval.
