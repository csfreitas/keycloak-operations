# Explicit infrastructure connections — 2026-09-11

Scope: eliminate ambient Kubernetes/OpenShift credential selection in existing application infrastructure clients. This is a security foundation for portable onboarding, **not** completion of installation binding, new connection CRUD or host/container collectors.

## Behavior and migration

- Every cluster target must have an explicit infrastructure `credential-ref` and namespace. No reference means no client; blank namespace or malformed credentials fail before client creation.
- Credential entries select exactly one mode: `token` with explicit `api-server-url`, `kubeconfig`, or `in-cluster=true`. Empty entries and conflicting modes fail closed. Keycloak/metrics credential resolution remains separate.
- Token mode starts with `Config.empty()` and never imports ambient kubeconfig/system settings. HTTPS is required except HTTP on loopback for local fixtures; user-info, query and fragment in API-server URLs are rejected. TLS verification remains enabled unless explicitly configured otherwise.
- Explicit kubeconfig requires a unique selected context and matching cluster/user; no missing-context fallback. Static inline token or certificate material is required. Exec/auth-provider plugins and external certificate/key/CA paths are rejected **before** merging. Basic-auth credentials are not supported by this infrastructure path. Supply a dedicated static, inline-credential kubeconfig through the approved secret channel, or use token/mounted-service-account mode. Never paste or commit real credentials.
- `in-cluster=true` reads only the standard mounted pod service-account token and CA, uses `https://kubernetes.default.svc` and the explicit target namespace. It does not inspect local kubeconfig, external service-account paths or environment-selected API servers. Missing mount fails closed. Rotation is re-read on target client resolution; this does not promise seamless renewal for already in-flight operations or long-lived watches.
- Cache identity hashes the complete resolved configuration and binding context in memory. A changed token, CA, proxy/TLS or selected kubeconfig configuration cannot intentionally reuse the previous cached identity. Raw configuration and hash are not output, logged or persisted.
- Legacy no-argument environment discovery returns UNKNOWN without probing. The old global discovery flags no longer enable a scan; registered target configuration is authoritative.

The OpenShift ConfigMap example now binds `presentation-cluster` explicitly and declares `mcp.credentials.presentation-cluster.in-cluster=true`. This is a template edit, not a deployment or real OpenShift validation. Existing configurations relying on implicit in-cluster mode need migration.

Implementation uses Fabric8 7.8.0's explicit empty config and static kubeconfig parsing; the version's local source was inspected to confirm automatic configuration and executable-provider behavior. [Fabric8 configuration documentation](https://github.com/fabric8io/kubernetes-client) explains why default auto-configuration is inappropriate for target isolation.

## Evidence

- Starting HEAD: `572cb7b`, preexisting dirty D1/portable architecture changes preserved; no commit/push.
- Baseline: Java 21 via jenv, `mvn clean verify`: **286 passed**, 0 failures/errors, **9 opt-in ITs skipped**, SUCCESS at 2026-09-11 00:59:40 -03:00. Log `/private/tmp/kcops-explicit-binding-baseline.log`.
- An intermediate focused test correctly exposed an implementation mistake: Fabric8's kubeconfig token is initially in `autoOAuthToken`, not `oauthToken`. The explicit loader now transfers that static token and clears context/automatic refresh sources. The positive kubeconfig test passed on rerun.
- Focused tests cover missing/conflicting credential modes, missing endpoint/scope, ambient endpoint/basic-auth/TLS overrides, static kubeconfig, executable-provider denial, missing service-account mount and explicit mount fixtures, and target/token/cache isolation.
- Final regression: **303 passed**, 0 failures/errors, **9 opt-in ITs skipped**, `mvn clean verify` SUCCESS at 2026-09-11 01:07:32 -03:00 using Java 21 via jenv. Log `/private/tmp/kcops-explicit-binding-final.log`. This slice adds 17 tests and strengthens existing global-discovery and client-isolation cases. `git diff --check` passed.
- Final Podman inventory: **0 containers, 0 volumes**. No global prune, manual deletion of user resources, real cluster access, commit or push.

Tests use mock cluster servers and temporary generated credentials/mount fixtures only. No actual cluster or user's kubeconfig was read. Real in-cluster deployment, client-certificate compatibility, token expiry during an in-flight operation and real host/container discovery remain unverified. UI was not changed in this slice.

## Next installation-binding slice

Historical handoff at the end of this connection slice. The subsequent [exact installation binding ledger](exact-installation-binding-2026-09-11.md) records the implementation and remaining limits; the paragraph below describes the pre-binding state.

`InventoryService.findWorkload` still uses first-match heuristics. Next: persist an explicit installation binding (connection identity, namespace, API group/version, kind, name, UID), validate configuration/schema migration and API/UI contracts, use the binding to select resources and reconcile UID changes, and prove two installations in one namespace never cross-associate. Candidate enumeration/confirmation and multi-runtime installation metadata must share that contract. Do not describe the current collector as exact-installation safe before this gate.
