# OpenShift deployment templates

These manifests are **templates**, not a verified or turnkey RHBK deployment. Do not apply their placeholder values. No OpenShift/RHBK or production OIDC compatibility claim is established by local unit tests.

The backend uses the authenticated `oidc` profile and stays read-only. It has one replica because MCP sessions and SSE use in-process state. HTTP and management listeners explicitly bind inside the pod; only the HTTP service port is routed externally. The management port is limited by the NetworkPolicy and is used by probes.

Before deployment:

1. Build the intended backend image and replace its example image reference with a verified digest. The example registry/tag is not a release availability guarantee.
2. Provision Identity A in a platform IdP: backend bearer-token validation, intended audience (`keycloak-operations` by default), and the `ops-assessor` role for authorized demonstration callers. Configure `keycloak-operations-platform-identity` through your secret-management process; do not commit real values. Set `OIDC_AUDIENCE` and the IdP audience mapper consistently; set `OIDC_ROLE_CLAIM_PATH` to the role claim you intend to trust (default `realm_access/roles`, alternatively backend client roles). If a client secret is not required for your selected OIDC setup, omit that placeholder key rather than transmitting placeholder text.
3. Provision a **different** Identity B in the target RHBK, with the least privilege required for the demonstrated reads. Populate `keycloak-mcp-credentials`; do not grant `realm-admin` as a convenience default.
4. Provision PostgreSQL and populate `keycloak-operations-database`. Adapt the database NetworkPolicy selectors to its exact namespace/pod labels. These templates intentionally do not create a persistent database, PVC, or external cloud resource.
5. Set `RHBK_NAMESPACE` in the backend deployment and scope the assessor service account bindings to the required namespace/resources. Review the cluster-scoped privileges separately; use namespace-only access where adequate.
6. Review all NetworkPolicy egress destinations for the actual cluster, IdP, RHBK, PostgreSQL, and optional metrics provider. Existing broad HTTPS/Keycloak ports are template allowances, not a final least-privilege policy. Configure Prometheus/OpenShift Monitoring separately using [the monitoring guide](../../docs/openshift-monitoring.md); without it, metrics must remain explicitly not configured, never fabricated.
7. Verify TLS trust and the configured token audience, negative anonymous access to REST/MCP/SSE, target isolation, and safe read-only failures before permitting external access. Keep the management port private.

The ConfigMap registers `presentation-rhbk` and disables inherited localhost lab targets. It explicitly grants only `READ,ASSESS` on that target to `ops-assessor`; it does not grant planning, approval, or write permissions. Use a fresh dedicated platform database for this configuration template, or reconcile preexisting target registrations explicitly: the composite registry can contain records seeded by an earlier configuration.

The Web UI manifests are separate. Their image must include the intended auth configuration and validated token acquisition/forwarding. The current UI's OIDC-ready hooks are not proof of a completed browser login flow; validate that flow before treating the UI route as presentation-ready. Direct authenticated MCP/API usage can be validated independently.

Never substitute `local-lab` for `oidc` to work around a cluster authentication problem. See [the identity model](../../docs/identity-model.md) for grants, lab boundaries, and limitations.
