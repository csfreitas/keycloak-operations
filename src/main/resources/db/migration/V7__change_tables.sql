-- Controlled administration change lifecycle (milestone 0.8)
-- Secrets must never be stored in plaintext columns or JSON payloads.

CREATE TABLE change_records (
    id                     VARCHAR(36)  PRIMARY KEY,
    target_id              VARCHAR(128) NOT NULL,
    environment            VARCHAR(32)  NOT NULL,
    resource_type          VARCHAR(64)  NOT NULL,
    resource_id            VARCHAR(255) NOT NULL,
    realm                  VARCHAR(255) NOT NULL,
    operation              VARCHAR(32)  NOT NULL,
    status                 VARCHAR(32)  NOT NULL,
    risk                   VARCHAR(32),
    policy_decision        VARCHAR(64),
    policy_reason          TEXT,
    requires_approval      BOOLEAN      NOT NULL DEFAULT TRUE,
    plan_fingerprint       VARCHAR(128),
    baseline_fingerprint   VARCHAR(128),
    approval_fingerprint   VARCHAR(128),
    desired_state          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    baseline_state         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    diff_json              JSONB        NOT NULL DEFAULT '[]'::jsonb,
    operations_json        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    actor                  VARCHAR(255),
    approved_by            VARCHAR(255),
    approved_at            TIMESTAMPTZ,
    rejected_by            VARCHAR(255),
    rejected_at            TIMESTAMPTZ,
    rejection_reason       TEXT,
    applied_at             TIMESTAMPTZ,
    verification_status    VARCHAR(32),
    verification_message   TEXT,
    verification_json      JSONB,
    result_message         TEXT,
    idempotency_key        VARCHAR(128),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_change_records_target_id ON change_records (target_id);
CREATE INDEX idx_change_records_status ON change_records (status);
CREATE INDEX idx_change_records_created_at ON change_records (created_at);
CREATE INDEX idx_change_records_target_status ON change_records (target_id, status);
CREATE UNIQUE INDEX idx_change_records_idempotency
    ON change_records (target_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
