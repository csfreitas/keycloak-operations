# SecOps/IAM synthetic acceptance data

These **39 authored cases** describe proposed P1/P2 behavior. They are not collected
user data, model responses, executable change requests or evidence that the product
already implements password-age assessment, identity-event investigation or user writes.
See the [scenario catalogue](../../docs/development/secops-iam-scenarios.md) for scope,
requirements, missing capabilities and live acceptance gates.

| File | Planned scenarios | Cases |
|---|---|---|
| [Password age](password-age.json) | IAM-06 / P1 | 13 |
| [Identity investigation](identity-investigation.json) | SECOPS-01 / P1 | 11 |
| [Temporary access and containment](temporary-access.json) | HLP-01 + SECOPS-02 / P2 | 15 |

## Local integrity checks

With Node 24 available, from the repository root:

```sh
node --test dev/secops-scenarios/*.test.mjs
```

No dependency install, runtime service, credential, network, provider or model is
needed. The tests read only these fixtures and requirement definitions. CI runs this
offline catalogue check alongside, but separately from, reference-profile tests.
No persistent volume/image/container is created by these Node checks.

## Fixture contract (catalogVersion 1)

- `synthetic: true`, `status: PLANNED`, scenario/milestone/requirement references.
- `context` supplies synthetic registered target/realm IDs and a fixed UTC `asOf`.
  Product versions are **assumptions** with `capabilityValidation: NOT_RUN`, never
  proof of Keycloak or RHBK support. No real environment or host is selected.
- `policy` is an explicitly synthetic proposed policy, not a production default.
  The age policy uses 90 × 24 hours with equality allowed, not calendar-date rounding.
- Each case has a unique `id`, minimal independent `input` and authored `expected`
  decision/reason, JSON-pointer `evidencePaths` relative to that input, forbidden
  claims, and `grantsAuthority: false`. Inputs illustrate one boundary and are not
  full API schemas; omitted fields must not become permissive runtime defaults.
- `expected` is a test oracle for **future** service/transport/lab tests. Current
  checks establish only fixture integrity and internal consistency (including time
  arithmetic); no runtime evaluator is called and none is faked here.
- `PARTIAL` in the population case is coverage, not an individual compliance status.
  `PLAN_REQUIRED`, `RECONCILE_REQUIRED` and similar labels are catalogue vocabulary,
  not new values added to public report/change APIs.

The minimum set is not exhaustive: the catalogue additionally requires real
membership/provider semantics, out-of-order events, concurrent writers, scheduler
failure, sponsor revocation and end-to-end target/realm authorization. Each future
implementation must add real assertions against its service instead of treating a
passing fixture check as application acceptance. No product/profile/report schema
version or permission changes; AGT1 remains read-only and model trials stay separate.
