# AGENTS.md — AI coding workflow

**Source of truth:** Git, code, tests, and repository documentation.  
**Do not** rely on conversation history. **Do not** treat prompts as specifications.

## Start here

1. Read [`docs/project-state.md`](docs/project-state.md)
2. Read [`docs/milestones/README.md`](docs/milestones/README.md) and identify the **CURRENT** / next milestone
3. Read that milestone specification
4. Read related [`docs/requirements/`](docs/requirements/) and [`docs/architecture/`](docs/architecture/)
5. Run `mvn clean verify` and record the baseline before coding

More policy: [`docs/development/ai-assisted-development.md`](docs/development/ai-assisted-development.md)

## Architecture invariants (summary)

- Multi-target: every op uses `targetId` — never LLM-supplied system URLs or credentials
- MCP and REST share application services
- No raw Admin REST / kubectl / oc / PromQL tools
- Assessments are deterministic (Evidence → Rules → Findings); LLM does not decide PASS/FAIL
- PostgreSQL is not a TSDB
- Never return, persist, or log secrets; preserve target isolation
- Prefer Admin REST; use capability detection for version-specific features
- Operational tools are read-only by default

## Validation

```bash
mvn clean verify
```

Do not claim an integration was tested unless it actually ran. Opt-in ITs: `RUN_KEYCLOAK_IT`, `RUN_RHBK_IT`, `RUN_PROMETHEUS_IT`.

## Git

Do **not** commit or push without explicit authorization. Do not tag `*-SNAPSHOT` releases.

## Final report (when finishing a milestone)

Keep it short: baseline, features, main files, verify result, test counts, ITs actually run, limitations, next milestone.
