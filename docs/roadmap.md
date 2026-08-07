# Roadmap

The project is evolving from a **Keycloak MCP Server** into a **Keycloak / RHBK Operations Platform**.

SemVer (`pom.xml`) and roadmap milestones are aligned starting at **0.4.0-SNAPSHOT**.
Earlier work (0.1–0.3) shipped under artifact version `0.1.0` as foundational milestones.

## 0.1 — Keycloak admin read-only

## 0.2 — Multi-target

## 0.3 — PostgreSQL, persistence, audit, history

## 0.4 — OpenShift / Kubernetes discovery

## 0.5 — Health Check & Assessment Depth

## 0.6 — Prometheus / metrics integration *(current — 0.6.0-SNAPSHOT)*

- Semantic metrics (no PromQL from clients); Prometheus / OpenShift Monitoring providers
- Performance summary + optional SLO evidence; `performance` rule pack
- Profiles: `*-production-performance`; metrics optional unless profile requires them
- REST/MCP metrics tools; lab Prometheus compose + smoke script

## 0.7 — Web UI / Fleet dashboard

## 0.8 — Snapshots, historical comparison, configuration drift

## 0.9 — Scheduled assessments, alerts, notifications

## 1.0 — Production-ready Operations Platform
