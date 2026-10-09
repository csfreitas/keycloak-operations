# AI-assisted development

AI coding agents may be used when contributing to this repository.

## Principles

- **AI agents are implementation assistants**, not the source of product requirements.
- **Requirements** ([`docs/requirements/`](../requirements/)) are authoritative for *what*.
- **Architecture** and **ADRs** ([`docs/architecture/`](../architecture/), [`docs/adr/`](../adr/)) are authoritative for *how* / *why*.
- **Milestones** ([`docs/milestones/`](../milestones/)) define delivery slices and acceptance.
- **Tests** (`mvn clean verify` and relevant ITs) validate behavior.
- **Git** records history; conversation transcripts are not.
- **Prompts are not specifications.** Large temporary Agent prompts belong outside the public docs tree (for example local `.agent-prompts/`, gitignored).
- **Human review remains required** for correctness, security, and documentation quality.

## Agent entrypoint

See root [`AGENTS.md`](../../AGENTS.md) for the short bootstrap checklist.

Its continuity checklist also governs slice completion: reconcile project context, affected architecture, milestone acceptance, Unreleased changes and artifact-version status before handoff. Preserve dated validation evidence and distinguish implemented, locally verified, live-verified and planned behavior. Documentation-only maintenance does not require creating containers or presenting old test results as a fresh run.

## Contributor responsibility

Whether or not AI tools were used, contributors remain responsible for tests, security expectations, and reviewable changes. See [`CONTRIBUTING.md`](../../CONTRIBUTING.md).
