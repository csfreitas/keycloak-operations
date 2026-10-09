# Installation-scoped networking — local validation, 2026-09-11

## Scope and behavior

This backend slice restores networking inventory after [exact installation binding](exact-installation-binding-2026-09-11.md). It observes configured associations, **not** successful routing, DNS, TLS certificate validity, OpenShift admission, endpoint readiness or availability. No cluster access, endpoint probing, deployment, commit or push was performed.

The collector accepts only the workload already resolved by `InventoryService` after target authorization and root binding verification. It rereads scoped workload identities and rejects a replaced root. It lists Services in the configured namespace and evaluates their entire nonempty selector against the bound Deployment/StatefulSet template. A matching template from another workload or any selected Pod outside the approved controller-ownership chain makes the Service ambiguous and excludes it. Deployment pods are associated through ReplicaSet ownership; StatefulSet pods through direct ownership. A zero-replica installation can have configured networking without being healthy or reachable.

Ingress associations use exact Service backend names and numeric/named Service ports (TCP), per rule/path or default backend. Mixed-application Ingress objects return only the matching paths/hosts. TLS is recorded per matching host entry; no Secret is read. A default backend without a host has unknown TLS configuration, not an invented hostname. Resource backends are unsupported and explicitly reduce coverage.

OpenShift Route associations use exact Service references, including alternate backends. If an alternative is outside the verified installation, the Route is ambiguous and excluded—even at weight zero. This conservative behavior avoids silently treating shared configurations as exclusive. All accepted exposures are returned in deterministic order; multiple hosts no longer silently select the first one.

## API and evidence contract

`NetworkingInfo` retains its three legacy summary fields and adds:

- `services`: associated Service names and observed UIDs.
- `exposures`: allowlisted resource kind/name/UID, Service name/UID, host, path and nullable `tlsConfigured`.
- `complete`: whether all queried, supported configuration association steps completed without a gap.

The outer inventory supplies target/namespace and collection time. `host` is populated only for a single unambiguous host; unknown/default or multiple hosts produce null. Legacy `tlsEnabled=true` requires explicit TLS configuration on every returned exposure; false must **not** be interpreted as proof that all exposure lacks TLS. Use the individual exposure fields and warnings. TLS configuration does not imply valid certificates, enforced HTTPS, admission or end-to-end encryption.

Denied, unavailable, ambiguous, unsupported or truncated queries produce generic `networking` warnings and `complete=false`; raw API error text is not included. Already verified exposure entries may remain visible in a partial result. Assessment input `keycloak.route.present` is emitted only for complete association results; partial results never become evidence that an ingress/route is absent. A successfully queried empty result covers only the supported API types, not every possible exposure mechanism.

No annotations, certificates, private keys, Secret values, endpoint addresses or unrestricted resource dumps are exported. Existing REST/MCP redaction and target authorization remain in place. Service and exposure UIDs are observed provenance, not a new persistent binding or cryptographic attestation.

## Limits and operational requirements

- Queries use the explicit target namespace and existing connection. Required reads: Deployments, StatefulSets, ReplicaSets, Pods, Services, networking.k8s.io/v1 Ingresses, plus route.openshift.io/v1 Routes for configured OpenShift connections. Existing assessor RBAC templates already list these resource types; no permission expansion or deployment was performed.
- Each list asks for at most 501 resources and rejects counts above 500 or a continuation token rather than treating a partial page as complete. Foreign-namespace or incomplete identity payloads fail closed. This is a resource-count guard, **not** a total response-byte, nested-path-count or end-to-end collection-time budget. Those limits remain follow-up work.
- Lists reveal namespace-scoped resources internally to establish selector exclusivity; only associated metadata is returned. Reads are sequential and not an atomic snapshot. Future changes can alter associations after collection.
- Selectorless/ExternalName Services cannot be verified by this path and cause an explicit coverage gap. This is intentionally conservative even when such a Service may be unrelated. Manually managed EndpointSlices, Gateway API, controller-specific rewrites/annotations, certificates, DNS, Route target-port resolution/admission, Service reachability and NodePort/LoadBalancer exposure evaluation remain unsupported/unverified.
- Template exclusivity is checked for Deployments/StatefulSets and current Pod ownership. It does not prove that a different controller can never create a matching Pod later.
- No confirmation UI, candidate CRUD or audited reconciliation was added. This is the prerequisite networking backend slice; confirmation remains the next delivery. Host/container collectors remain on the portable roadmap.

Primary semantics: [Kubernetes Services](https://kubernetes.io/docs/concepts/services-networking/service/), [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/), [OpenShift Route API](https://docs.redhat.com/en/documentation/openshift_container_platform/4.20/html/network_apis/route-route-openshift-io-v1). Version documentation is a design reference, not evidence of testing that platform release.

## Evidence ledger

- HEAD: `572cb7b`; existing uncommitted changes preserved.
- Baseline: Java 21 through per-command jenv, `mvn clean verify`, **321 passed**, 0 failures/errors, **9 optional integration tests skipped**, SUCCESS at 2026-09-11 01:28:50 -03:00. Log `/private/tmp/kcops-networking-baseline.log`.
- Fabric8 local mock tests cover two installations in one namespace, shared selectors, foreign pods, controller-owned pods, mixed Ingress paths, numeric/named ports, default backend, per-host TLS, multiple hosts, Route alternatives, secret-field exclusion, permission denial, continuation tokens, foreign namespace payloads, UID replacement and assessment evidence suppression. These mock APIs are not an OpenShift cluster.
- Final `mvn clean verify`: **338 passed**, 0 failures/errors, **9 optional integration tests skipped**, SUCCESS at 2026-09-11 01:36:00 -03:00 (58.519 s), Java 21 through jenv. Log `/private/tmp/kcops-networking-final.log`. This slice adds 17 tests and strengthens the existing inventory ownership fixture.
- `git diff --check` passed. Podman after completion: **0 containers, 0 volumes**, four existing reusable images preserved. No global prune or manual deletion of user resources.

Real RHBK/OpenShift controller behavior and live networking remain unverified. No UI changes or UI/real-IdP reruns were made in this slice. Next: authorized candidate confirmation and reconciliation, followed by validation on the dedicated maintainer-approved cluster.
