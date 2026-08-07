# ADR 0002 — Admin REST as Keycloak integration boundary

- **Status:** Accepted
- **Date:** 2026-08 (initial platform)

## Context

Keycloak exposes Admin REST and also internal/server APIs that vary by version and packaging (Community vs RHBK).

## Decision

Prefer the public **Admin REST API** (via Keycloak Admin Client / stable adapter) as the integration boundary. Do not depend on Keycloak internal server APIs when Admin REST can satisfy the use case. Use capability/version detection for version-specific features.

## Consequences

- Better Community/RHBK compatibility.
- Some advanced diagnostics may be unavailable until exposed via supported APIs.
- `AdminApiV2Adapter`-style paths remain non-primary / capability-gated.
