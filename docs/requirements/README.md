# Requirements

Normative requirements for the Keycloak / RHBK Operations Platform.

## Purpose

Requirements answer **what** the platform must do. They are the product source of truth above prompts, conversation history, or ad-hoc Agent instructions.

```text
Requirements  →  Architecture / ADRs  →  Milestones  →  Implementation  →  Tests
```

## Conventions

| Prefix | Meaning |
|--------|---------|
| `FR-*` | Functional |
| `NFR-*` | Non-functional |
| `SEC-*` | Security |
| `COMPAT-*` | Compatibility |

IDs are stable. Prefer adding a new ID over renumbering.

### Normative language

| Word | Meaning |
|------|---------|
| **MUST** | Mandatory |
| **SHOULD** | Strongly recommended unless justified |
| **MAY** | Optional |

## Index

| Document | Scope |
|----------|--------|
| [functional-requirements.md](functional-requirements.md) | Capabilities (targets, admin, assessment, health, metrics, REST/MCP) |
| [non-functional-requirements.md](non-functional-requirements.md) | Isolation, resilience, bounds, testability |
| [security-requirements.md](security-requirements.md) | Credentials, SSRF, PromQL, TLS, redaction |
| [compatibility-requirements.md](compatibility-requirements.md) | Keycloak vs RHBK, capability detection, tested versions |

## Relationship to other docs

| Artifact | Role |
|----------|------|
| [Architecture](../architecture/README.md) | **How** requirements are realized |
| [ADRs](../adr/README.md) | **Why** a design was chosen |
| [Milestones](../milestones/README.md) | **When** a slice of requirements is delivered |
| Tests (`mvn clean verify`) | **Verification** |
| [AGENTS.md](../../AGENTS.md) | AI coding workflow — not a requirements catalog |

Milestones SHOULD list the requirement IDs they implement or target.
