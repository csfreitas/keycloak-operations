# SecOps & IAM Co-pilot — scenario catalogue

Status: **PLANNED PRODUCT CAPABILITIES; SYNTHETIC ACCEPTANCE DATA ONLY**.
Updated 2026-09-19. Audience: operators, implementers and reviewers. This catalogue
turns the three proposed use cases into bounded backlog slices; it does not register
new MCP tools, enable event collection, authorize writes or evaluate a model.

## Delivery order and ownership

| ID / operator question | Backlog / requirements | Acceptance owner and next dependency |
|---|---|---|
| IAM-06 / Which finance-group users violate our password-age policy? | P1; FR-IAM-001/002/003, FR-EVID-001, FR-DOC-001 | IAM policy + privacy owner; prove population and credential-age source semantics first |
| SECOPS-01 / What evidence explains these geographically unusual logins? | P1 / ALT-01; FR-IAM-001/002/004, FR-OBS-001 | IAM/security owner; approve bounded event source and deterministic signal rule |
| HLP-01 / Prepare temporary consultant access in homologation | P2 / GOV-01 + CHG-01; FR-GOV-001/002, FR-CHANGE-001–016, SEC-CHANGE-001–005 | Operations + security owner; durable attempts, approval, concurrency and expiration gates first |
| SECOPS-02 / Prepare approved containment for this user | P2 / GOV-01 + CHG-01; FR-GOV-001/003, SEC-CHANGE-001–005 | Incident owner + distinct authorized approver; separate from SECOPS-01 investigation |

This order does not displace H1/D1/AGT1, the read-only presentation or the existing
P1/P2 dependencies. Statistical anomaly detection belongs to ALT-02 only after
history and measured quality, not merely because a rule compares two countries.
The [current tools](../tools.md) include user/group reads and controlled client
changes, not `keycloak_create_user`, `keycloak_disable_user` or
`keycloak_get_audit_events`. Those names in the proposal are examples, not contracts.
Future operations must be typed application services shared by MCP/REST, not raw
Admin REST, model-selected endpoints, shell, SQL or arbitrary PromQL.

## Common evidence and trust contract

- Bind registered target, installation, realm and stable resource IDs. Realm names
  remain display/adapter inputs where required; names such as "homologation" alone
  cannot establish installation identity, policy or authorization.
- Record distribution, observed build/version, credential provider, source and
  method revision, collection/as-of window, population, pagination, freshness,
  gaps and applicability. Unknown version/capability cannot inherit compatibility.
- Keycloak upstream and RHBK require separate version-specific acceptance. The
  synthetic Keycloak 26.7.1 and RHBK 26.6 labels are assumptions, not live evidence,
  a support matrix, a supported-upgrade recommendation or Red Hat certification.
- Enforce bounded queries, least privilege, target/realm/resource authorization,
  privacy purpose and retention before collection. Aggregate/pseudonymous output is
  default; identifiable drill-down needs separate authorized access. No password,
  hash, token, IP address or raw event payload goes to the model by default.
- Evidence, including hostile metadata, cannot grant permissions or alter rules.
  AI explains deterministic results and their uncertainty; it does not decide
  compliance, fraud, score, approval, risk or successful remediation.
- Current [AGT1](../milestones/agt1-reference-agent.md) remains READ/ASSESS with its
  fixed report tool. These scenarios do not extend its tool list or authorize a
  new provider/model evaluation. Synthetic fixture outputs are expected results,
  not observed model responses or platform findings.

## IAM-06 — organizational password-age assessment

Require a versioned, owner-approved policy: eligible population, stable group ID,
direct versus effective membership, human/service/disabled-account handling,
exceptions with expiry, credential types and exact UTC comparison. The example
policy allows an age of **at most 90 × 24 hours**; exactly 90 days is within policy,
one second beyond is outside. This is a fixture policy, not a universal security
recommendation, legal standard or promise of native password-policy equivalence.

Classify each eligible subject as `WITHIN_POLICY`, `OUTSIDE_POLICY`, `UNKNOWN` or
`NOT_APPLICABLE`, with evidence and a reason. Report population coverage separately:
a partial page never proves a complete group or denominator. State unknown counts
and scope before any percentage. Proven passwordless-only subjects can be N/A;
passwordless enrollment alone does not prove that password authentication is absent.

Prove that the source represents the last effective password change for the actual
provider/version. Last login, profile update, an unvalidated credential timestamp or
absence of reset events cannot substitute. Cover administrator resets, migrations,
multiple credentials and external changes. LDAP passwords are validated by LDAP,
not imported into Keycloak; a local record alone is not authoritative for their age.
See [Keycloak 26.7.1 storage mode](https://www.keycloak.org/docs/26.7.1/server_admin/#storage-mode).

Acceptance: 89/90/91-day controls plus exact-second boundaries; missing history,
retention below the requested window, federation, passwordless, future timestamps,
partial/duplicate population, effective membership changes, expired exceptions,
cross-target denial and unknown product/version. Do not report global compliance
from a partial sample or classify missing data as a policy violation automatically.

## SECOPS-01 — bounded identity-event investigation

Distinguish authentication/user events, Keycloak administrative events and the
Operations platform's own audit. The latter cannot prove where a user logged in.
Approve source, purpose, retention and access separately; missing/disabled event
storage is unavailable evidence, not zero logins or a healthy environment.

Use a reviewed deterministic rule with explicit observation window, event types,
minimum independent samples, deduplication and timestamp tolerances. Geographic
enrichment must identify method/dataset revision and uncertainty. VPNs, proxies,
NAT, IPv6 changes and clock skew are alternative explanations. No automatic IP
export or external geolocation service is enabled. Two-country activity is a signal
for investigation, not proof of compromise or a sufficient reason to block a user.

Acceptance: a complete two-event signal and benign/insufficient controls; duplicate
and out-of-order events; gaps and partial retention; wrong target/realm/subject;
source permission denial; unreliable geographic data; hostile event text and fake
approval; no blind fetch of linked URLs and no remediation triggered by an alert.
Preserve independent observed facts even when the overall evaluation is partial.

## HLP-01 and SECOPS-02 — separately governed changes

HLP-01 proposes target/realm, sponsor, purpose, stable user identity, minimal effective
roles (including composite/inherited roles), policy-bounded TTL and UTC `expiresAt`.
Temporary password or an expiry attribute is not enforced temporary access. Define
whether expiration disables login, removes grants or also terminates sessions, with
durable scheduling, restart recovery, measured lateness and operator escalation.
Refuse temporary-access promises before expiration enforcement is accepted. Do not
silently adopt an existing user or delete a partially created account as rollback.

SECOPS-02 starts a separate typed plan for an exact user and containment operation.
An alert does not approve it. Require authenticated approval tied to immutable plan,
target/resource/policy revisions and expiry, current drift checks, durable execution
attempt, read-back and audit. Protect service/break-glass identities by reviewed
policy. Unknown remote success requires reconciliation before retry; HTTP success
alone is not verified completion. These safety prerequisites also apply to HLP-01.

Disabling login, terminating sessions and invalidating already-issued tokens are
different claims. Keycloak's documented sign-out action does not universally revoke
outstanding access tokens; validate actual client/session/token behavior separately.
See [Keycloak 26.7.1 session sign-out](https://www.keycloak.org/docs/26.7.1/server_admin/#signing-out-all-active-sessions).

Acceptance: duplicate identity, wrong installation/realm, excess inherited privilege,
missing/expired/excess TTL, scheduler restart, sponsor revocation, expired/modified/
wrong-actor approval, prompt-forged approval, concurrent writers, success followed
by timeout, partial create/grant/expiry persistence, and residual access tokens.
No production execution or broad user administration is authorized by this plan.

## Validation ladder and stop criteria

1. **Delivered catalogue/fixtures:** check schema, unique IDs, links, evidence paths,
   policy arithmetic and minimum negative cases locally. These are catalogue
   integrity checks, not acceptance of the proposed application behavior.
2. **Future implementation:** test deterministic services and authorization with
   reproducible inputs, expected outcomes and measured failure behavior; transport
   contracts must agree. Do not copy fixture expectations into a pretend evaluator.
3. **Approved disposable lab:** verify actual Keycloak version/provider semantics,
   real denied requests, pagination, recovery and owned-resource cleanup; separately
   repeat against approved RHBK versions. Record unsupported cases honestly.
4. **Optional agent evaluation:** explicitly approve client/model, instructions,
   data scope and response budget; retain raw responses and review meaning as well
   as structure. Operator acceptance and product acceptance remain separate.

Stop at absent source capability, unclear identity/authorization, unapproved PII
collection, uncertain writes or unverified cleanup. Do not broaden permissions,
enable event storage, deploy a listener or infer human approval to finish a case.
See [synthetic fixture guide](../../dev/secops-scenarios/README.md),
[P1](../milestones/p1-iam-continuous-observability.md) and
[P2](../milestones/p2-governance-remediation.md).
