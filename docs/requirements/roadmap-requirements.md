# Roadmap extension requirements

Stable requirements derived from the already approved [roadmap](../roadmap.md). They define future acceptance, not implemented features. Existing FR/SEC/NFR/COMPAT requirements still apply; [milestones](../milestones/README.md) track delivery.

### FR-ONBOARD-001

Approved administrators **MUST** be able to register/revise a connection and logical environment without application source edits, using credential references and explicit endpoint/scope validation. Registration **MUST NOT** automatically grant target access. Operational callers **MUST NOT** provide arbitrary endpoints. Managed installation reconciliation **MUST** revalidate identity, revision, authorization and audit before use.

### FR-ONBOARD-002

An initial administrative preflight **MUST** be disabled by default and require an
explicit verified issuer/subject/role/REST-client match in one server-configured grant.
It **MUST** authorize before parsing the bounded closed draft or querying registry
metadata. It **MUST NOT** resolve candidate credentials, contact candidate endpoints,
persist drafts/targets/audit, grant access, modify global read-only or expose an MCP
tool. Local collision checks **MUST** distinguish unavailable from absent. Results
**MUST** omit submitted values and explicitly report no registration, connectivity,
credential-validity or destination-approval claim. This subset does not fulfill
FR-ONBOARD-001; see [contract 0.1.0](../registry-preflight.md).

### FR-ONBOARD-003

Registry definition ownership **MUST** be explicit, separate from installation-binding
ownership. Historical rows **MUST NOT** be automatically adopted from matching IDs or
configuration values. Configuration reconciliation **MUST** modify only known
configuration-owned rows, preserve normalized no-ops, use an ORM concurrency revision
and atomically audit effective changes/invalidation. A conflict or audit failure
**MUST** roll back the whole sync, never select configuration as a fallback. Database
absence/emptiness **MUST NOT** reactivate a configured namesake. Additive migration
**MUST** preserve prior definitions/history and expose populated-upgrade/adoption and
mixed-version rollback limits. See [ownership foundation](../architecture/registry-ownership.md).

### FR-OPERATOR-001

A product installation Operator **MUST** manage only Keycloak Operations lifecycle,
not the evaluated Keycloak/RHBK resources, their Operators or databases. The core
assessment/console/MCP services **MUST** remain portable and usable without this
installation controller. Platform availability **MUST NOT** imply healthy targets,
complete evidence, target access or production readiness. See [OP1](../milestones/op1-operator-installation.md).

### FR-OPERATOR-002

The installation API **MUST** be versioned, bounded and explicit about desired-state
ownership, generation-scoped status, idempotency, supported release transitions and
deletion/retention. It **MUST NOT** silently adopt foreign resources, delete referenced
data, override registry records, advertise unsupported HA or treat image rollback as
database rollback. Future declarative registry management **MUST** use the shared
administrative policy/API with explicit ownership and reviewed transfer semantics.

### SEC-OPERATOR-001

Installer, operational backend/UI and collectors **MUST** have distinct least-privilege
identities. Installation references **MUST** be limited to the permitted namespace
and typed sources; credentials **MUST NOT** enter CR/status/events/logs. Installation
**MUST NOT** grant target access, expose arbitrary workload/command/property overrides,
or weaken authentication/read-only controls. Registration and target mutation remain
separate authorities; any change to current global read-only/BIND semantics requires
an explicit reviewed policy slice and negative tests before activation.

### FR-EVID-001

Retained assessment inputs **MUST** identify target/installation, collection/source windows, coverage/gaps, evaluator/rule/profile/collector revisions and sanitization. Replay **MUST** use retained inputs without remote collection, preserve deterministic findings and reject unsupported revisions. Digests **MUST NOT** be represented as authenticity or complete correctness proofs.

### FR-DOC-001

Technical/executive reports **MUST** derive from a canonical authorized record and agree on facts, units, counts, windows and unknowns. Every conclusion **MUST** trace to evidence/rules; human review and optional AI explanation **MUST** remain distinct. Rendered PDF/DOCX support **MUST** include visual QA before delivery claims.

### FR-AGENT-001

The reference agent **MUST** be optional, versioned, read-only (READ/ASSESS), adaptable and tested for grounding, missing evidence, malicious metadata and cross-target denial. Validated client/model/authentication combinations **MUST** be recorded. No provider disclosure or additional authority is granted by its instructions; deterministic operation **MUST** work without a model.

### FR-AGENT-002

An optional question-driven operational client **MUST** be able to compose accepted semantic read capabilities across authorized infrastructure and IAM domains, not only explain an operations report. Capability discovery **MUST** declare actual domain/product/version support and distinguish denied, unsupported, uncollected, stale and ambiguous data. It **MUST NOT** expose arbitrary APIs/commands or expand AGT1 grants implicitly; each client/profile integration needs its own acceptance. See [AGT2](../milestones/agt2-access-aware-assistance.md).

### FR-AGENT-003

Operational answers **MUST** identify the authorized resource scope, observation windows, typed facts, evidence references and gaps, distinguishing generic guidance, observations, hypotheses and deterministic findings. Question-level call/time/page/byte/concurrency limits and ambiguity handling **MUST** be enforced outside the model. Follow-ups **MUST** reauthorize references and never infer omitted facts, global scores or resource identity from masked display text.

### FR-UI-004

The operator console **MUST** remain a first-class application for authorized environment inspection, assessment, health checks, evidence review and reports without an LLM. UI/REST and MCP **MUST** share application services, canonical facts and backend policy; AI interaction **MUST NOT** replace direct operator review. Parity **MUST** compare equivalent principal/client/delegation/data-class/disclosure context, not copy all UI data into model context. New domain screens and optional embedded chat **MUST** be tracked separately from existing UI capabilities, with source/scope/permission parity tests.

### SEC-AUTHZ-002

Restricted operational assistance **MUST** enforce explicit target/installation/namespace, realm/resource, operation and field/data-class permissions as applicable, through shared backend controls across MCP, REST/UI, search/lists, aggregates, assessments/reports, retained history, exports and events. Denial is the default. Human/client identity or delegation **MUST** be authenticated, never model-provided; outbound target credentials **MUST NOT** widen human grants. Legacy target-wide grants require explicit migration/review and **MUST NOT** be described as fine-grained enforcement. Counts, errors and indirect relationships **MUST NOT** disclose excluded resources.

### SEC-AI-003

Permission to read **MUST NOT** imply permission to disclose to a model/provider. Approved client/disclosure scope, PII projection and data minimization **MUST** precede context construction; credentials **MUST NOT** enter it. Saved evidence, conversation, caches and exports **MUST** be isolated and reauthorized by principal/client, resource scope, policy revision and disclosure context, including privilege changes and late results. Current session/token and already-disclosed-data limitations **MUST** be explicit; no promise of retroactive forgetting is permitted. Incoming platform tokens **MUST NOT** be passed through to target APIs.

### FR-IAM-001

Every IAM indicator **MUST** declare authorized source, actual version capability, target/scope, window, unit, method, denominator, freshness and missing-data behavior. Login attempts **MUST NOT** be equated with unique users. MFA policy, enrollment and actual use **MUST** remain distinct; financial or availability impact estimates **MUST** expose assumptions and approved inputs.

### FR-IAM-002

Granular identity analytics **MUST** require approved purpose/access/retention, bounded ingestion, deduplication and appropriate pseudonymization. Raw personal identifiers/event payloads **MUST NOT** enter default AI exports. Aggregate-only sources **MUST NOT** be used to fabricate individual-user metrics.

### FR-OBS-001

Continuous collection and alerts **MUST** be scoped, bounded, durable and auditable, with retention, retry/idempotency, duration/recovery, deduplication and expiring silences. External notification **MUST** be opt-in. Statistical anomalies **MUST** have sufficient history and measured quality; neither anomalies nor alerts authorize remediation.

### FR-IAM-003

Organizational password-age assessment **MUST** identify approved policy/revision, exact as-of boundary, eligible population and effective group membership, provider/version-specific authoritative change semantics and expiring exceptions. Per-subject WITHIN_POLICY, OUTSIDE_POLICY, UNKNOWN and proven NOT_APPLICABLE **MUST** remain distinct from population coverage. Missing history, last login, import/profile timestamps or unverified credential fields **MUST NOT** establish password age; partial pagination **MUST NOT** establish group-wide compliance. A 90-day example **MUST NOT** imply universal security policy or legal certification. See [IAM-06](../development/secops-iam-scenarios.md).

### FR-IAM-004

Identity-event investigations **MUST** distinguish user events, administrative events and platform audit; scope authorized target/realm/subject/window and retain source/version/retention/coverage plus deduplication and timestamp semantics. Geographic signals **MUST** expose method and uncertainty, alternative explanations and reviewed deterministic criteria. Missing or denied sources **MUST NOT** become zero activity. Event text, alerts and AI explanations **MUST NOT** establish compromise, grant approval, invoke external enrichment or authorize containment. See [SECOPS-01](../development/secops-iam-scenarios.md).

### FR-DRIFT-001

Semantic drift **MUST** compare compatible scoped evidence with known coverage and reviewed baselines/exceptions. Failed/denied collection **MUST NOT** imply deletion. Configuration intent, observed runtime and resource identity changes **MUST** remain distinct, including external GitOps ownership.

### FR-GOV-001

Production mutation workflows **MUST** persist execution attempts and reconcile uncertain outcomes before retry, validate current policy/context and coordinate competing plans. Required human/two-person approval **MUST** be enforced by identity/workflow controls, not inferred from an LLM response or UI acknowledgment.

### FR-GOV-002

Temporary-user provisioning **MUST** use an independently accepted typed lifecycle with explicit target/realm/identity, sponsor/purpose, allowlisted effective privileges and policy-bounded UTC expiry. Temporary password or an attribute **MUST NOT** be represented as enforced access expiration. Durable expiry/restart/reconciliation, maximum lateness, audit and escalation **MUST** be validated before temporary-access promises; partial create/grant/expiry outcomes **MUST NOT** be reported as complete or trigger blind retry/automatic deletion. Existing users **MUST NOT** be silently adopted. See [HLP-01](../development/secops-iam-scenarios.md); P2 safety gates remain prerequisites.

### FR-GOV-003

User containment **MUST** be a separately authorized typed, fingerprint-bound plan with resource policy, protected-identity handling, current drift/approval/expiry checks, durable attempts, read-back and audit. Alerts **MUST NOT** grant mutation authority. Disabled state, session termination and validity of outstanding tokens **MUST** be reported separately; uncertain outcomes require reconciliation before retry. See [SECOPS-02](../development/secops-iam-scenarios.md); client-change support **MUST NOT** imply user-change acceptance.

### FR-SPI-001

SPI assessment **MUST** retain authorized artifact/source digest, build/dependency/runtime provenance, tested scope and unknowns. Missing source or self-reported artifact output **MUST NOT** be treated as completed security review.

### FR-SPI-002

Custom-code build/test execution **MUST** occur in an approved isolated runtime outside the platform/customer target, using approved artifact/suite identities, bounded resources/egress, trusted supervision, durable attempts and verified cleanup. Assessment, execution and promotion **MUST** have distinct authority; no arbitrary model-supplied code/command tool is allowed.

### FR-OPS-001

Operational-readiness claims **MUST** identify supported scope and evidence for compatibility, populated migrations, backup/restore, retention, load and recovery against pre-agreed budgets. Deployment topology, independent security review, maintenance ownership and release procedure **MUST** be documented before a production-readiness claim.

### FR-SCORE-001

An operational rating **MUST** use a reviewed, versioned deterministic rubric with a 1–100 scale, declared profile, control/dimension weights, aggregation, rounding and critical-risk caps. The AI **MUST NOT** assign or alter the rating. Duplicate findings and equivalent resource populations **MUST NOT** create accidental repeated deductions. The rubric **MUST NOT** be represented as official product certification.

### FR-SCORE-002

Every available rating **MUST** retain target/assessment/report identity, exact arithmetic, control results including PASS and applicability decisions, observed-versus-required values, versioned evidence references, reasons, applicable caps and corrective guidance. Supported transports **MUST** agree on the canonical authorized result; a bounded finding export **MUST NOT** be used to reconstruct a complete score. Replay **MUST** use retained inputs rather than today's live rules or feeds.

### FR-SCORE-003

Coverage, freshness and confidence **MUST** remain separate from operational condition. Required missing/expired evidence, unknown denominators, unsupported evaluation scope or no applicable controls **MUST** yield an unavailable rating, not a favorable exclusion or a numeric minimum. Proven N/A exclusions **MUST** retain their justification and weight redistribution. Reduced coverage **MUST NOT** improve the available rating or weaken current availability guards.

### FR-SCORE-004

Rule applicability **MUST** distinguish Keycloak upstream from RHBK and match observed version/build, runtime and required capabilities. Declared tags, mixed installations, unobserved instances and unsupported tool versions **MUST** remain explicit limitations. Historical comparisons **MUST** disclose changes in scope, distribution/version, profile, rubric/rules and external source revisions; old results **MUST NOT** be overwritten. Public support policy **MUST NOT** imply customer entitlement.

### FR-SCORE-005

Lifecycle, patch availability and CVE applicability **MUST** be evaluated separately using dated official supplier evidence and installed-artifact identity. The overall environment-rating profile **MUST** require this evidence for its declared component scope; a limited configuration assessment **MUST NOT** substitute for it. Backports, fixed/not-affected/under-investigation states, source disagreement and unknown matches **MUST** be handled explicitly. Only confirmed applicable unresolved vulnerabilities may incur a CVE deduction; missing results **MUST NOT** imply zero vulnerabilities. Public-feed collection **MUST** use approved bounded read-only destinations, provenance/attribution and expiring cache or verified offline snapshots, without transmitting customer inventory or secrets. No automatic patching or exploitation is authorized.
