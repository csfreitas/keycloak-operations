# Database schema

See Flyway migrations in `src/main/resources/db/migration/`.

## ER overview

```mermaid
erDiagram
  targets ||--o{ target_tags : has
  targets ||--o{ assessment_runs : has
  assessment_runs ||--o{ assessment_findings : has
  targets ||--o{ health_check_runs : has
  health_check_runs ||--o{ health_check_results : has
  targets ||--o{ environment_snapshots : has
  environment_snapshots ||--o{ inventory_snapshots : has
  targets ||--o{ audit_events : optional
```

## Notable columns

- **targets**: credential **refs** only; `observability` JSONB
- **assessment_findings**: `engine_status` (OPEN/PASS/WARNING/FAIL/…) + `lifecycle_status`; optional `resource_type` / `resource_id` / `resource_name` (V6)
- **assessment_runs**: V6 adds `evidence_completeness`, `confidence`, `category_scores`, rule counters
- **health_check_results**: V6 adds `duration_ms`
- **audit_events**: `trace_id`, sanitized `params` JSONB
- **environment_snapshots**: `snapshot_hash` SHA-256 of normalized summary

Indexes: `target_id`, `created_at`, `assessment_id`, `severity`, `status`/`lifecycle_status`, `trace_id`.
