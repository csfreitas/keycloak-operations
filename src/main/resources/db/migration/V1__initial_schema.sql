-- Targets and tags (no secrets — credential refs only)

CREATE TABLE targets (
    id                      VARCHAR(128) PRIMARY KEY,
    display_name            VARCHAR(255)  NOT NULL,
    product_type            VARCHAR(32)   NOT NULL,
    environment             VARCHAR(32)   NOT NULL,
    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
    keycloak_url            VARCHAR(1024) NOT NULL,
    keycloak_auth_realm     VARCHAR(255)  NOT NULL,
    keycloak_client_id      VARCHAR(255)  NOT NULL,
    keycloak_credential_ref VARCHAR(255)  NOT NULL,
    infra_type              VARCHAR(32),
    infra_cluster_id        VARCHAR(255),
    infra_namespace         VARCHAR(255),
    infra_credential_ref    VARCHAR(255),
    observability           JSONB,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE target_tags (
    target_id VARCHAR(128)  NOT NULL REFERENCES targets (id) ON DELETE CASCADE,
    tag_key   VARCHAR(255)  NOT NULL,
    tag_value VARCHAR(1024),
    PRIMARY KEY (target_id, tag_key)
);

CREATE INDEX idx_targets_created_at ON targets (created_at);
CREATE INDEX idx_targets_enabled ON targets (enabled);
CREATE INDEX idx_target_tags_target_id ON target_tags (target_id);
