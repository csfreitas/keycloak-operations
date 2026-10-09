-- Existing targets remain unbound: never guess which live installation they own.
ALTER TABLE targets ADD COLUMN installation_api_version VARCHAR(128);
ALTER TABLE targets ADD COLUMN installation_kind VARCHAR(32);
ALTER TABLE targets ADD COLUMN installation_name VARCHAR(253);
ALTER TABLE targets ADD COLUMN installation_uid VARCHAR(128);
ALTER TABLE targets ADD CONSTRAINT installation_binding_complete CHECK (
    (installation_api_version IS NULL AND installation_kind IS NULL AND installation_name IS NULL AND installation_uid IS NULL)
    OR (installation_api_version IS NOT NULL AND installation_kind IS NOT NULL AND installation_name IS NOT NULL AND installation_uid IS NOT NULL
        AND infra_cluster_id IS NOT NULL AND infra_namespace IS NOT NULL AND infra_credential_ref IS NOT NULL)
);
