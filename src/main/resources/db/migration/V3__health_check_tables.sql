-- Health check runs and per-check results

CREATE TABLE health_check_runs (
    id             VARCHAR(36)  PRIMARY KEY,
    target_id      VARCHAR(128) NOT NULL REFERENCES targets (id),
    overall_status VARCHAR(32)  NOT NULL,
    trigger_type   VARCHAR(32)  NOT NULL,
    summary        JSONB,
    started_at     TIMESTAMPTZ  NOT NULL,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE health_check_results (
    id              VARCHAR(36)  PRIMARY KEY,
    health_check_id VARCHAR(36)  NOT NULL REFERENCES health_check_runs (id) ON DELETE CASCADE,
    target_id       VARCHAR(128) NOT NULL,
    check_name      VARCHAR(255) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    message         TEXT,
    details         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_health_check_runs_target_id ON health_check_runs (target_id);
CREATE INDEX idx_health_check_runs_created_at ON health_check_runs (created_at);
CREATE INDEX idx_health_check_runs_status ON health_check_runs (overall_status);
CREATE INDEX idx_health_check_results_health_check_id ON health_check_results (health_check_id);
CREATE INDEX idx_health_check_results_target_id ON health_check_results (target_id);
CREATE INDEX idx_health_check_results_status ON health_check_results (status);
