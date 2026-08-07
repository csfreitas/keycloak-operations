# ADR 0004 — No arbitrary endpoints from MCP/REST

- **Status:** Accepted
- **Date:** 2026-08 (initial platform)

## Context

Agent tools that accept free-form URLs, kubectl, or queries create SSRF and privilege-escalation risks.

## Decision

MCP and REST **must not** accept arbitrary Keycloak, Kubernetes/OpenShift, Prometheus, or shell endpoints/commands. Only registered target bindings and semantic operations are allowed.

## Consequences

- No raw Admin REST proxy, kubectl/oc tools, or raw PromQL tools.
- New capabilities need semantic APIs + target configuration.
- Security reviews focus on binding and isolation, not open proxies.
