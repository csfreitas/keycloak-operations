-- Milestone 0.5: assessment depth + finding subject + health duration

ALTER TABLE assessment_runs
    ADD COLUMN IF NOT EXISTS evidence_completeness INTEGER,
    ADD COLUMN IF NOT EXISTS confidence VARCHAR(16),
    ADD COLUMN IF NOT EXISTS category_scores JSONB,
    ADD COLUMN IF NOT EXISTS rules_evaluated INTEGER,
    ADD COLUMN IF NOT EXISTS rules_matched INTEGER,
    ADD COLUMN IF NOT EXISTS rules_skipped INTEGER,
    ADD COLUMN IF NOT EXISTS rules_not_evaluated INTEGER;

ALTER TABLE assessment_findings
    ADD COLUMN IF NOT EXISTS resource_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS resource_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resource_name VARCHAR(255);

ALTER TABLE health_check_results
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT;
