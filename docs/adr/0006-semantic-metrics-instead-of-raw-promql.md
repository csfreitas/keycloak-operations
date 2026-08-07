# ADR 0006 — Semantic metrics instead of raw PromQL

- **Status:** Accepted
- **Date:** 2026-08 (milestone 0.6)

## Context

Allowing agents to submit PromQL enables injection, cross-tenant reads, and unbounded queries.

## Decision

Expose **semantic metrics** (categories, windows, scopes). The backend builds controlled PromQL with escaping, mandatory selectors, and bounds. Callers never submit raw PromQL.

## Consequences

- Metrics catalog and query builder are first-class components.
- Not every PromQL expression is available — only catalogued semantics.
- Security tests cover injection and multi-target isolation.
