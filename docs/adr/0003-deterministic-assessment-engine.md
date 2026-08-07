# ADR 0003 — Deterministic assessment engine

- **Status:** Accepted
- **Date:** 2026-08 (assessment foundation; deepened in 0.5)

## Context

LLMs are useful for explanation but unreliable for compliance PASS/FAIL decisions.

## Decision

Assessments follow **Evidence → Rule packs → Findings → Scoring**. Rules are declarative/deterministic. The LLM **must not** decide PASS/FAIL.

## Consequences

- Rule packs and evidence catalogs are product artifacts.
- Missing evidence yields `NOT_EVALUATED` / partial completeness — not false PASS.
- MCP/REST expose results; they do not invent scores.
