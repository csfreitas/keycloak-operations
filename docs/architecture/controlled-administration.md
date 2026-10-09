# Controlled administration & change management

How the platform performs **safe, semantic, target-bound** writes against Keycloak/RHBK.

Related: [ADR 0007](../adr/0007-plan-approve-apply-change-model.md),
[milestone 0.8](../milestones/0.8-controlled-administration.md),
[milestone 0.8.1](../milestones/0.8.1-realm-client-administration.md),
[security](security.md), [persistence](persistence.md).

## H1 safety update

Identity is derived from the authenticated caller (or explicit `local-lab` marker), never actor/approver request text. Approval/rejection require separate `APPROVE`. Lists require an authorized `targetId`. Metadata-only legacy updates no longer accept PKCE; typed security settings own that semantic change. V8 adds policy/target-context/plan-integrity fingerprints; old pending plans remain readable/rejectable but need replanning before approval/apply, with a new idempotency key. Reusing an existing key for different normalized intent is rejected. Approval/apply revalidate integrity and current policy; same-plan lifecycle operations hold a DB row lock.

Remaining before production writes: durable attempt/reconciliation after remote success or uncertain timeout, coordination across different plans for one resource, expiry and genuinely human-only approval. MCP still exposes approval to separately authorized principals; it is not proof of human presence. The presentation uses read-only credentials and does not depend on writes. See [roadmap](../roadmap.md).

## Principle

Planned user-specific workflows are now bounded by the
[HLP-01 / SECOPS-02 catalogue](../development/secops-iam-scenarios.md), including
effective privileges, sponsor/expiry, partial provisioning and protected identities.
They reuse the intended application-service/policy boundary only after P2 durable
attempts, human approval, recovery and authorization are accepted. Current client
supports are not user supports: no create/disable-user tool, expiry scheduler or
session/token-revocation guarantee is delivered by the catalogue. Its synthetic
decisions are neither runtime statuses nor execution authority.

AI and REST callers never receive unrestricted write access.

```text
User / AI
    │
    ▼
Semantic Change Request   (targetId + resource + desired state)
    │
    ▼
Change Planning           (read current → diff → risk → policy)
    │
    ▼
Change Plan               (fingerprint / baseline hash)
    │
    ▼
Explicit Approval         (bound to plan fingerprint)
    │
    ▼
Apply                     (Admin REST via adapter only)
    │
    ▼
Read-back Verification
    │
    ▼
Audit
```

The LLM may request and explain changes. It **MUST NOT** decide authorization,
risk, approval requirement, policy outcome, or verification result.

## Never expose

- Raw Keycloak Admin REST paths or methods
- Arbitrary JSON mutation tools
- Credentials, passwords, client secrets, tokens, private keys

## Domain model

| Concept | Role |
|---------|------|
| ChangeRequest | Desired semantic mutation (not raw HTTP) |
| ChangePlan | Planned ops + safe diff + risk + policy + fingerprints |
| ChangeOperation | Single property-level mutation within a plan |
| ChangeDiff | ADDED / REMOVED / CHANGED / UNCHANGED (secrets redacted) |
| ChangeRisk | LOW / MEDIUM / HIGH / CRITICAL (deterministic) |
| ChangePolicyDecision | ALLOW / APPROVAL_REQUIRED / DENY |
| ChangeApproval | Approver + timestamp bound to plan fingerprint |
| ChangeExecution | Apply attempt metadata |
| ChangeVerification | Read-back comparison result |
| ChangeResult | Terminal outcome for callers |

Statuses (typical): `PLANNED` → `WAITING_APPROVAL` → `APPROVED` → `APPLYING` →
`APPLIED` / `VERIFIED` / `FAILED` / `REJECTED` / `EXPIRED`.

## Authorization

Target-scoped permissions:

| Permission | Meaning |
|------------|---------|
| READ | Read target configuration |
| ASSESS | Run assessments / health |
| PLAN | Create plans and diffs |
| APPROVE | Approve/reject a plan using trusted identity |
| WRITE | Apply approved non-admin changes |
| ADMIN | High-impact administrative applies (future) |

Global `mcp.read-only=true` (default) denies APPROVE/WRITE/ADMIN, as well as BIND for installation setup.
PLAN remains available so operators can dry-run.

## Environment policy

Defaults (configurable):

| Env | WRITE | Notes |
|-----|-------|-------|
| DEV | ALLOW or APPROVAL_REQUIRED by risk | LOW may skip explicit approval |
| TEST / HML / STAGING | APPROVAL_REQUIRED | |
| PRD | APPROVAL_REQUIRED | Always for production writes |
| Any | DELETE | DENY in 0.8 foundation |

## Integrity & concurrency

- **Plan fingerprint** — deterministic hash of planned operations.
- **Baseline fingerprint** — hash of observed current state at plan time.
- Approval stores the approved fingerprint; apply refuses mismatches (`APPROVAL_INVALID`).
- Before apply, re-read resource; baseline drift → `CHANGE_CONFLICT` / `REPLAN_REQUIRED`.
- Legacy scalar operation fingerprints remain readable for history; structured collection plans use canonical typed values. V8 safety-context requirements deliberately prevent approval/application of pre-context pending plans: create a fresh plan with a new idempotency key. Historical readability is not execution compatibility.

## Verification

After Admin API mutation success:

1. Read resource again
2. Compare to desired state
3. Persist `VERIFIED` or `VERIFICATION_FAILED`

HTTP 2xx alone is insufficient.

## Metadata admission and outward projection

`ChangeMetadataGuard` separates authoritative change state from lossy presentation.
After target authorization, planning rejects recognizable credential-bearing input
with `INVALID_ARGUMENT`, before normalization, provider lookup, fingerprints or
persistence. This covers resource identifiers, idempotency keys and the supplied
semantic values; it never substitutes redaction markers into desired configuration.
Generated baseline/desired state, operations and diffs are checked before retention.

Observation checks inspect the complete JSON-shaped `ClientRepresentation`, including
attributes and unrelated fields that an update would resubmit. Only the known
`secret` and `registrationAccessToken` fields are excluded from that check; both are
explicitly cleared before outbound create/update. Recognizable credentials elsewhere
cause `CHANGE_CONFLICT` with a fixed `REPLAN_REQUIRED` diagnostic. The guard does not
silently strip unrelated client configuration to make a write succeed.

Approval/apply check retained executable state as well as the existing policy and
integrity constraints. Explicit verification checks the stored plan too. Unsafe or
unavailable read-back metadata produces a failed, **inconclusive** verification with
no raw mismatch payload; it is not evidence of a successful apply or a definite
configuration mismatch. Verification output, rejection reasons and audit payloads
receive separate presentation filtering.

Service responses, including historical records, use sanitized copies. Stored legacy
plans and fingerprints are not rewritten, and sanitized copies are never used for
authorization, integrity, baseline comparisons or execution. Canonical change/target
IDs and trusted actor/approver/rejector identities remain intact. Reading an old
record does not certify its original stored bytes or authorize its execution.

REST `McpExceptionMapper` filters recognizable credentials from domain-error messages
without changing HTTP status or error code. MCP `ChangeTools` does the same and
reconstructs tool errors without their original cause/suppressed exceptions; unknown
failures use a fixed diagnostic. Authorization, read-only policy and audit flow are
unchanged. Other legacy MCP tool error paths are not covered by this correction.

These controls recognize the formats supported by `SensitiveDataFilter`, not arbitrary
unlabelled/encoded secrets, and do not constitute universal prompt-injection protection
or production-write certification. See the
[change metadata evidence and limitations](../development/h1-change-metadata-2026-09-19.md).

## Persistence

Flyway migration `V7` introduces `change_records` (aggregate lifecycle + JSON plan/diff/verification).
V8 adds the approval safety context described above. New executable state is admitted
unchanged only after the metadata guard; descriptive output is filtered separately.
No migration or historical-row rewrite is introduced by this metadata correction.

## Surfaces

| Surface | Role |
|---------|------|
| Application service | `ChangeManagementService` — sole business logic |
| MCP | `keycloak_get/list/approve/reject/apply/verify_change` + semantic plan tools |
| REST | `/api/v1/changes...` |
| Web UI | Minimal pending/detail/history views (not a full admin console) |

## UI presentation boundary

Change detail state and action controls belong to a single route visit; stale read,
action, error and completion callbacks cannot cross visits. A synchronous guard blocks
duplicate submissions, and a reload removes old controls. Detail response IDs must
match the requested change; action responses must also match its target. Change lists
are keyed by explicit target/status and reject pages containing foreign-target records.
These are defensive UI checks, not permission grants or proof of human approval.
Discarding an obsolete response does not cancel or undo a server operation.

The [19 September navigation evidence](../development/d1-change-navigation-2026-09-19.md)
uses real components with synthetic HTTP responses. No actual approval/apply was
performed; production-write recovery and all existing backend policy gates remain open.

## Proof-of-concept mutation (0.8)

Controlled non-sensitive **client configuration update** (allowlisted properties such as
display name / description; PKCE moved exclusively to typed security operations). Demonstrates the full lifecycle
without delete, password, or secret workflows.

## Typed client URL sets (0.8.1 Slice 1)

The typed `ClientUrlChangeRequest` replaces complete desired sets for `redirectUris` and/or `webOrigins`. Null means unchanged; an explicit empty collection means remove all values. `ClientUrlSettingsChangeSupport` validates and deterministically normalizes each set, produces item-level additions/removals for review, and retains structured lists in `ChangeOperation` for persistence and fingerprints.

Apply reads a fresh trusted `ClientRepresentation`, verifies the relevant baseline only, changes the allowlisted URL fields, clears secret material defensively, updates through `StableAdminApiAdapter`, and reads back normalized values for verification. Risk classification is transition-aware: exact values are at least MEDIUM, while path wildcards, non-loopback HTTP, and the Web Origin `+` sentinel are HIGH. Unsafe additions are denied by default in production.

## Typed client security and flows (0.8.1 Slice 2)

`ClientSecurityChangeRequest` exposes only PKCE, Authorization Code flow,
Implicit flow, Direct Access Grants, service accounts, and public/confidential
client semantics. `ClientSecuritySettingsChangeSupport` normalizes PKCE to
`S256` or `NONE`, validates effective service-account/client-authentication
combinations, and owns property-level diff, apply, and read-back verification.

Risk is based on the transition, not the Java value type. Enabling legacy or
credential-bearing grant paths, weakening PKCE, and changing client
authentication semantics are HIGH-risk. Production policy denies the unsafe
weakening subset by default. Apply starts from a fresh trusted representation,
mutates only requested allowlisted fields, clears secret material, preserves
unrelated configuration, and uses the existing fingerprint-bound lifecycle.

## Typed client lifecycle (0.8.1 Slice 3)

`ClientCreateChangeRequest` exposes only `clientId`, display metadata, enabled state,
public/confidential semantics, Authorization Code flow, Direct Access Grants, and
service accounts. The protocol is fixed to `openid-connect`; Implicit flow, arbitrary
attributes, complete representations, supplied secrets, delete, and secret rotation
are not part of creation. Conservative defaults create a disabled public client with
Authorization Code enabled and credential-bearing grants disabled.

Creation is HIGH risk and always requires approval under the current policy. Planning
verifies absence, fingerprints an `exists=false` baseline, and apply rejects a client
that appeared after planning. `ClientEnabledChangeRequest` treats enable as HIGH risk
and disable as MEDIUM. Both paths use the existing target authorization, fingerprint,
approval, audit, idempotency, stale-plan, secret-clearing, and read-back controls.
Production also denies creation with Direct Access Grants already enabled, preventing
the creation path from bypassing the Slice 2 weakening policy.

Broader realm/client/user/flow/IdP administration remains incremental after **0.8.1**. Fleet reporting/onboarding and platform authorization are prioritized before expanding all administration domains.
