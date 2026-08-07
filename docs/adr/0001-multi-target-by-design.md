# ADR 0001 — Multi-target by design

- **Status:** Accepted
- **Date:** 2026-08 (initial platform)

## Context

Operators manage many Keycloak/RHBK environments. A single-URL MCP server does not scale and invites SSRF if agents pass URLs.

## Decision

Every operational call is bound to a registered `targetId`. Endpoints and credentials are resolved from the target registry / credential providers — never from LLM-supplied URLs or secrets.

## Consequences

- Target registry and `TargetResolver` are core platform components.
- All tools/APIs require `targetId`.
- Isolation tests are mandatory across admin, infra, and metrics.
