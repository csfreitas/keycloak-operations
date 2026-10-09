# ADR 0004 — No arbitrary endpoints from MCP/REST

- **Status:** Accepted
- **Date:** 2026-08 (initial platform)

## Context

Agent tools that accept free-form URLs, kubectl, or queries create SSRF and privilege-escalation risks.

## Decision

Operational MCP and REST **must not** accept arbitrary Keycloak, Kubernetes/OpenShift, Prometheus, or shell endpoints/commands. Only registered target bindings and semantic operations are allowed.

[ADR 0013](0013-registry-preflight-before-registration.md) clarifies a narrow
administrative exception: an explicitly authorized, default-closed REST preflight
accepts a bounded candidate URL as inert draft data. It performs no candidate DNS,
credential resolution, requests or writes. This is not an operational endpoint/proxy
or destination approval and adds no MCP capability.

## Consequences

- No raw Admin REST proxy, kubectl/oc tools, or raw PromQL tools.
- New capabilities need semantic APIs + target configuration.
- Security reviews focus on binding and isolation, not open proxies.
