# Roadmap

The project is evolving from a **Keycloak MCP Server** into a **Keycloak / RHBK Operations Platform**.

SemVer (`pom.xml`) and roadmap milestones are aligned starting at **0.4.0-SNAPSHOT**.
Earlier work (0.1–0.3) shipped under artifact version `0.1.0` as foundational milestones.

## 0.1 — Keycloak admin read-only

## 0.2 — Multi-target

## 0.3 — PostgreSQL, persistence, audit, history

## 0.4 — OpenShift / Kubernetes discovery *(current — 0.4.0-SNAPSHOT)*

- Real `InfrastructureClientFactory` (per-target OpenShift/Kubernetes clients)
- Target-aware `EnvironmentDiscovery`
- Infrastructure inventory (workload, pods, topology, HPA, PDB, routes/ingress)
- Evidence catalog + collectors wired to inventory
- Snapshots use sanitized inventory (no basic placeholder)
- Rule packs via `rules/index.yaml`, profile filtering, duplicate ID detection
- CI workflow + Fabric8 mock tests
- Manual validation path: CRC / OpenShift Local

## 0.5 — Health Check & Assessment Depth *(next)*

Use the evidence produced in 0.4 for HA / zone / PDB / HPA / resources / scheduling /
security / ingress / production configuration rules — without reinventing collectors.

## 0.6 — Prometheus / metrics integration

## 0.7 — Web UI / Fleet dashboard

## 0.8 — Snapshots, historical comparison, configuration drift

## 0.9 — Scheduled assessments, alerts, notifications

## 1.0 — Production-ready Operations Platform
