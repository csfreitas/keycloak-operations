ALTER TABLE targets ADD COLUMN installation_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE targets ADD COLUMN installation_managed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE installation_discovery_runs (
    id VARCHAR(36) PRIMARY KEY,
    target_id VARCHAR(128) NOT NULL REFERENCES targets(id),
    actor TEXT NOT NULL,
    context_hash VARCHAR(64) NOT NULL,
    binding_revision BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    candidates JSONB NOT NULL
);
CREATE INDEX installation_runs_target_expiry ON installation_discovery_runs(target_id, expires_at);
