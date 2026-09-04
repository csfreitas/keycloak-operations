# ADR 0008 — AI assists; deterministic backend decides

- **Status:** Accepted
- **Date:** 2026-09

## Context

MCP makes fleet discovery, assessment, reporting, and controlled administration accessible to AI agents. Natural-language reasoning is useful for orchestration and explanation, but model output is non-deterministic and may omit evidence, hallucinate state, or select unsafe actions.

## Decision

AI is an assistance and orchestration layer over typed MCP tools. Evidence collection, health, assessment, scoring, authorization, risk, policy, approval, concurrency checks, apply, and verification remain deterministic backend responsibilities shared with REST and the Web UI.

Model-generated explanations are optional derived content. They must be distinguishable from deterministic facts and preserve provenance to report/evidence identifiers. Core platform workflows must work without an LLM provider.

## Consequences

- The platform can offer an AI-native experience without delegating security decisions to a model.
- MCP tools remain semantic, target-bound, compact, and auditable.
- New AI features require context bounds, redaction, provenance, and evaluation for hallucination and target leakage.
- Arbitrary Admin REST, PromQL, endpoints, credentials, or mutation payloads remain forbidden.
- Explanation quality can evolve independently from deterministic operational correctness.
