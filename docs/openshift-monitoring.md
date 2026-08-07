# OpenShift Monitoring integration

Set target metrics type to `OPENSHIFT_MONITORING`. Namespace scope is preferred;
cluster scope is explicit via `observability.metrics.scope=CLUSTER`.

Endpoint defaults to in-cluster Thanos Querier when unset
(`platform.metrics.openshift.endpoint` can override).

Credentials use `credential-ref` → `CredentialProvider.getMetricsCredentials`
(bearer / basic). Tokens are never logged or returned to MCP clients.
