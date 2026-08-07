-- Audit event trail (sanitized params only)

CREATE TABLE audit_events (
    id          VARCHAR(36)  PRIMARY KEY,
    trace_id    VARCHAR(64),
    source      VARCHAR(32)  NOT NULL,
    tool        VARCHAR(255),
    target_id   VARCHAR(128),
    operation   VARCHAR(255),
    status      VARCHAR(32)  NOT NULL,
    duration_ms BIGINT,
    params      JSONB,
    metadata    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_target_id ON audit_events (target_id);
CREATE INDEX idx_audit_events_created_at ON audit_events (created_at);
CREATE INDEX idx_audit_events_trace_id ON audit_events (trace_id);
CREATE INDEX idx_audit_events_status ON audit_events (status);
CREATE INDEX idx_audit_events_source ON audit_events (source);
