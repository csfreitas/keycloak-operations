# Demo readiness — 15 January 2027

Status: **IN PROGRESS — local identity/discovery/installation-confirmation foundations implemented; browser end-to-end and live RHBK/OpenShift gates NOT VERIFIED**.

## Local checkpoint — 2026-09-11

The working tree now includes OIDC/PKCE and authenticated event transport, explicit approved infrastructure connections, exact installation identity, scoped networking association and REST/UI candidate confirmation with transactional audit. [Architecture](../architecture/overview.md), [project state](../project-state.md) and [latest local evidence](../development/installation-onboarding-2026-09-11.md) distinguish implementation from live acceptance. The recorded 358 backend and 94 UI passes, with nine skipped opt-in integrations, do not close the go/no-go checklist below. New connection/target registration, host/container collectors and retained-evidence replay remain open.

Development versions are aligned in the D1 increment. Next: finish acceptance of the [D1 browser/identity workflow](d1-local-workflow.md) on approved local fixtures before [D2](d2-rhbk-openshift.md). [Executable milestones](README.md) provide implementation scopes; this document remains the presentation go/no-go authority. Earlier validation ledgers remain historical evidence. The presentation year is inferred from the supplied 15 January date; session duration remains unconfirmed.

## Submitted session promise

An experimental open-source MCP operations platform collects structured evidence from RHBK, Keycloak, OpenShift, Kubernetes and Prometheus; deterministic rules evaluate availability/security; health checks and runtime metrics support investigation; an AI assistant explores findings and explains evidence. Boundaries include target isolation, least privilege and controlled tools. The AI is not the source of truth.

## Traceability and acceptance

| Statement | Required demonstration | Gate |
|---|---|---|
| Discover RHBK | Caller lists only permitted targets; product/version and runtime evidence shown | Real RHBK integration, not configured type alone |
| Assess HA | Intentional lab topology produces expected finding and workload/evidence | Version-scoped tests + actual OpenShift observations; no customer outage injection |
| Assess security | Distinct realms yield distinct stable findings | Reverse collection order; 403/truncation never turns into PASS |
| Health checks | Components show status, latency, timestamp and gaps | Service HEALTHY/CRITICAL separate from collection COMPLETE/PARTIAL |
| Runtime metrics | Target-bound metric with units/window; missing source stays unavailable | Missing provider/no samples/zero traffic distinguished |
| Explain evidence | AI cites report/finding/source and limitations | Findings identical without LLM; injected metadata cannot authorize tools |
| Security boundaries | Anonymous/other-target access denied; read-only cannot approve/apply | REST/MCP/discovery/list/SSE and live token validation |
| Customer engagements | Sanitized technical/executive export | No customer PII, secrets or unsupported certification |

Business/IAM reporting is an approved product extension. Verified login/failure/application activity can be optional demo content. Unique users, MFA-use analytics, learned anomalies and SPI execution are not dependencies of the submitted abstract. See [roadmap](../roadmap.md).

## Environments and responsibilities

The optional reference agent is a planned product deliverable (AGT-01/AGT-02 in the [roadmap](../roadmap.md#agente-de-referência--planejado-ainda-não-implementado)), not an implemented or mandatory platform dependency. D1 prototypes the versioned profile/setup guide; D2 validates a specific MCP client/model/OIDC combination against the selected RHBK environment; D3 evaluates grounding and safety; D4 rehearses and freezes that combination. The profile uses READ/ASSESS only, cites evidence, preserves uncertainty and never replaces backend policy or deterministic rules. Model/provider choice and external data disclosure require organizational approval. UI/API and deterministic reporting remain usable without it.

1. Local: disposable Community targets, platform DB and Prometheus; named/labeled resources with transient storage; metrics-present and unavailable fixtures.
2. RHBK/OpenShift: maintainer provisions dedicated non-customer namespace/workload; exact product/platform versions; read-only credentials and namespace RBAC; no Secret contents. Provisioning requires that environment/access, not inferred authority.
3. Negative identity: only target A readable; target B denied including history/events. The reference AI has READ/ASSESS only; no DISCOVER/BIND/PLAN/APPROVE/WRITE/ADMIN grants in the principal demo.

Installation setup is separate from the principal read-only demonstration. Candidate discovery needs READ + DISCOVER; confirmation additionally requires BIND and disabling global read-only after administrator review. Do not grant BIND to the reference agent or disable that global guard as a demo convenience. Existing-target confirmation binds platform metadata and does not write to the cluster.

## Modular live sequence

Duration remains provisional: reserve 10–15 minutes until session allocation is confirmed.

1. State experimental status, versions, scope and read-only identity.
2. Discover permitted target; show observed product/runtime.
3. Assess; inspect one HA and one security finding with entity/evidence.
4. Check health; explain current state versus configuration posture.
5. Inspect runtime metric/window and one unavailable source.
6. Generate report; ask AI to explain findings and prioritize suggested actions using IDs.
7. Show denied cross-target access/write; close with limits and roadmap.

Prepare non-production fixtures instead of changing customer configurations. Do not deploy SPIs, execute model-generated code or approve writes in the principal demo.

## Go/no-go

- [ ] H1 regressions pass on recorded source revision.
- [ ] Actual RHBK/OpenShift integration, version and permissions recorded.
- [ ] Positive/negative fixtures pass twice consecutively.
- [ ] Findings carry rule revision, entity, observed value, rationale and applicability.
- [ ] Incomplete evidence has no favorable overall score/conclusion.
- [ ] Metrics isolation, units, freshness and empty-data behavior verified.
- [ ] AI preserves evidence/uncertainty; unsafe instructions do not alter authority.
- [ ] Reference agent profile, setup guide and evaluation cases are versioned; actual client/model/authentication compatibility is recorded, not assumed.
- [ ] Reference agent passes normal/missing-data/cross-target/malicious-metadata cases without critical disclosure, unauthorized actions or invented conclusions; no-AI fallback is rehearsed.
- [ ] Credentials valid, least-privilege and not exposed in recording/artifacts.
- [ ] Network/certificates/quotas checked one week before and shortly before session.
- [ ] Rehearsal timed; sanitized recording/export fallback available and dated.
- [ ] Presenter reviews claims and limitations before freeze.

If infrastructure fails on the day, show dated recording/stored evidence and label it offline. If real RHBK validation never occurred, revise claims before the session. Record commit/working-tree, Java via jenv, versions, tests/skips, fixtures and final resource inventory in a validation report. No automatic publication, commit, push, release or external deployment.
