# Architecture

How the Keycloak / RHBK Operations Platform is structured.

## Start here

1. [overview.md](overview.md) — platform layers and flows
2. [multi-target.md](multi-target.md) — target registry and isolation
3. [assessment-engine.md](assessment-engine.md) — evidence → rules → findings
4. [security.md](security.md) — redaction, credentials, least privilege
5. [persistence.md](persistence.md) — PostgreSQL / Flyway (not a TSDB)
6. [observability.md](observability.md) — semantic metrics integration
7. [controlled-administration.md](controlled-administration.md) — plan / approve / apply / verify
8. [portable-environment-discovery.md](portable-environment-discovery.md) — connection/environment/installation/observation boundaries; current cluster binding and future collectors
9. [operations-reporting.md](operations-reporting.md) — implemented on-demand JSON/Markdown report
10. [trustworthy-reporting.md](trustworthy-reporting.md) — planned retained evidence, replay and reviewed documents
11. [ai-assisted-operations.md](ai-assisted-operations.md) — deterministic backend and optional reference agent
12. [iam-business-observability.md](iam-business-observability.md) — planned indicators, source semantics, privacy and alerts
13. [spi-assurance.md](spi-assurance.md) — planned artifact review and isolated execution
14. [operational-rating.md](operational-rating.md) — proposed explainable 1–100 rating, product/version applicability and public lifecycle/patch/CVE evidence
15. [access-aware-operations-assistant.md](access-aware-operations-assistant.md) — first-class operator console and optional question-driven assistant; AGT2 partial with first restricted configuration-read channel, remaining multi-tool/domain gates planned
16. [operator-managed-platform.md](operator-managed-platform.md) — accepted installation/portable-hub boundaries; OP1 not implemented
17. [operator-installation-contract.md](operator-installation-contract.md) — draft 0.1 fields, ownership, lifecycle/status and non-deployable example; no served CRD
18. [registry preflight](../registry-preflight.md) — implemented opt-in administrative draft checks, contract 0.1.0; no registration or candidate connectivity
19. [registry-ownership.md](registry-ownership.md) — V11, configuration provenance, ORM revisions and atomic audit; no automatic legacy adoption or public registration

Implementation status and current next step: [project-state](../project-state.md) and [executable milestones](../milestones/README.md). A design document is not evidence its components are implemented or live-verified.

## Related

- Requirements: [`../requirements/`](../requirements/)
- ADRs: [`../adr/`](../adr/)
- Milestones: [`../milestones/`](../milestones/)

Detailed catalogs (tools, rules, evidence keys, REST paths) remain as topic docs under `docs/` and are linked from the overview and milestones.
