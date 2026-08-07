-- Assessment runs and findings

CREATE TABLE assessment_runs (
    id           VARCHAR(36)  PRIMARY KEY,
    target_id    VARCHAR(128) NOT NULL REFERENCES targets (id),
    profile      VARCHAR(255) NOT NULL,
    score        INTEGER      NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    trigger_type VARCHAR(32)  NOT NULL,
    summary      JSONB,
    started_at   TIMESTAMPTZ  NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE assessment_findings (
    id               VARCHAR(36)  PRIMARY KEY,
    assessment_id    VARCHAR(36)  NOT NULL REFERENCES assessment_runs (id) ON DELETE CASCADE,
    target_id        VARCHAR(128) NOT NULL,
    finding_key      VARCHAR(255) NOT NULL,
    title            VARCHAR(512) NOT NULL,
    category         VARCHAR(255),
    severity         VARCHAR(32)  NOT NULL,
    engine_status    VARCHAR(32)  NOT NULL,
    lifecycle_status VARCHAR(32)  NOT NULL,
    description      TEXT,
    evidence         JSONB,
    impact           TEXT,
    recommendation   TEXT,
    reference_urls   JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_assessment_runs_target_id ON assessment_runs (target_id);
CREATE INDEX idx_assessment_runs_created_at ON assessment_runs (created_at);
CREATE INDEX idx_assessment_runs_status ON assessment_runs (status);
CREATE INDEX idx_assessment_findings_assessment_id ON assessment_findings (assessment_id);
CREATE INDEX idx_assessment_findings_target_id ON assessment_findings (target_id);
CREATE INDEX idx_assessment_findings_severity ON assessment_findings (severity);
CREATE INDEX idx_assessment_findings_lifecycle_status ON assessment_findings (lifecycle_status);
CREATE INDEX idx_assessment_findings_created_at ON assessment_findings (created_at);
