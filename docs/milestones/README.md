# Milestones

Product delivery slices. Status from Git / `pom.xml` / code — not chat history.

## Current delivery priority

The [complete roadmap](../roadmap.md) and [January demo specification](2027-01-demo-readiness.md) govern delivery order: **H1 trust hardening → D1 local read-only workflow → D2 real RHBK/OpenShift → D3 trustworthy documents → D4/D5 readiness**. Essential authorization is brought forward from 0.8.3. Remaining 0.8.1 realm Slice 4 and broad administration are deferred to P2, not silently declared complete. IAM/business reporting, alerts and isolated SPI assurance are specified as separate tracks. Historical milestone numbers below remain references, not a mandate to continue CRUD before these gates.

| Milestone | Description | Status |
|-----------|-------------|--------|
| [0.1](0.1-keycloak-admin-readonly.md) | Keycloak Admin Read-only | COMPLETED |
| [0.2](0.2-multi-target.md) | Multi-target | COMPLETED |
| [0.3](0.3-platform-foundation.md) | Platform Foundation | COMPLETED |
| [0.4](0.4-infrastructure-discovery.md) | Infrastructure Discovery | COMPLETED |
| [0.5](0.5-health-assessment.md) | Health & Assessment | COMPLETED |
| [0.6](0.6-prometheus-metrics.md) | Prometheus Metrics | COMPLETED |
| [0.6.1](0.6.1-metrics-hardening.md) | Metrics Hardening | COMPLETED |
| [0.7](0.7-web-ui.md) | Web UI | COMPLETED |
| [0.8](0.8-controlled-administration.md) | Controlled Administration & Change Management | COMPLETED |
| [0.8.1](0.8.1-realm-client-administration.md) | Realm & Client Administration | IN PROGRESS *(Slices 1–3 implemented; remaining realm slice deferred to P2)* |
| [0.8.2](0.8.2-fleet-reporting-target-onboarding.md) | Fleet Reporting & Target Onboarding | PLANNED *(report foundation implemented early)* |
| 0.8.3 | Platform Authorization & Governance | CORE CONTROLS IMPLEMENTED IN H1; full governance remains planned |
| 0.8.4 | Users, Groups & Roles | PLANNED |
| 0.8.5 | Authentication Flows & Client Scopes | PLANNED |
| 0.8.6 | Identity Providers & Advanced Realm Configuration | PLANNED |
| 0.9 | Schedules / alerts | PLANNED |
| 1.0 | Production-ready platform | PLANNED |

Milestones list **requirement IDs**, scope, and acceptance — not Agent prompts.  
Workflow: [`../../AGENTS.md`](../../AGENTS.md) · HEAD snapshot: [`../project-state.md`](../project-state.md)

## Git mapping (selected)

| Commit | Notes |
|--------|--------|
| `198c237` | Conceptual 0.1–0.3 (`0.1.0`) |
| `4d01a9a` | 0.4 |
| `5f9a1b7` | Track `target` Java package |
| `c0d00a3` | 0.5 |
| `81eff56` | 0.6 |
| `9ebadc9` | 0.6.1 hardening |
| `610e444` | 0.7 Web UI |
| `0eddf31` | 0.8 Controlled Administration |
