-- Environment and inventory snapshots (hashes for change detection; not a TSDB)

CREATE TABLE environment_snapshots (
    id            VARCHAR(36)  PRIMARY KEY,
    target_id     VARCHAR(128) NOT NULL REFERENCES targets (id),
    snapshot_hash VARCHAR(64)  NOT NULL,
    summary       JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE inventory_snapshots (
    id                       VARCHAR(36)  PRIMARY KEY,
    target_id                VARCHAR(128) NOT NULL REFERENCES targets (id),
    environment_snapshot_id  VARCHAR(36)  REFERENCES environment_snapshots (id) ON DELETE SET NULL,
    inventory_type           VARCHAR(64)  NOT NULL,
    summary                  JSONB,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_environment_snapshots_target_id ON environment_snapshots (target_id);
CREATE INDEX idx_environment_snapshots_created_at ON environment_snapshots (created_at);
CREATE INDEX idx_inventory_snapshots_target_id ON inventory_snapshots (target_id);
CREATE INDEX idx_inventory_snapshots_created_at ON inventory_snapshots (created_at);
CREATE INDEX idx_inventory_snapshots_env_snapshot_id ON inventory_snapshots (environment_snapshot_id);
