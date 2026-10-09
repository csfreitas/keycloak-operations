# Portable discovery foundation — 2026-09-11

Scope: accepted platform-independent architecture/requirements and the first target discovery guard. Preserve the preexisting uncommitted D1 identity and reference-agent roadmap changes. HEAD at start: `572cb7b`; no commit/push, deployment or real cluster access authorized/performed in this slice.

Baseline: `JENV_VERSION=21 jenv exec mvn clean verify`, **280 passed**, 0 failures/errors, **9 opt-in ITs skipped**, build SUCCESS at 00:53:12 -03:00. Log: `/private/tmp/kcops-portable-discovery-baseline.log`.

Changes: target discovery with absent/NONE/VM infrastructure or missing explicit cluster credential references reports scoped UNKNOWN without invoking the global discovery/configuration or cluster factory. It does not mislabel a declared VM as confirmed observed VM. Null target does not probe infrastructure. Explicit supported cluster binding continues to resolve its own client. Six regression tests added; no new VM/container runtime collector, onboarding API or schema migration.

Final regression: **286 passed**, 0 failures/errors, **9 opt-in ITs skipped**, build SUCCESS at 00:56:12 -03:00; log `/private/tmp/kcops-portable-discovery-final.log`. New tests are mocked routing/isolation tests, not live VM/Docker/Podman/OpenShift integration. UI was not changed by this slice; the prior D1 UI results remain historical. `git diff --check` passed. Final Podman check: **0 containers, 0 volumes**. No manual prune or deletion of user resources.

Remaining: remove remaining ambient fallback in all infrastructure factory/credential paths and legacy global discovery; bind exact installations instead of first matches; implement capability-aware onboarding and platform collectors; validate each runtime independently. Do not confuse Podman hosting of test PostgreSQL with implemented Podman inventory. Updated requirements describe intended support, not current implementation.
