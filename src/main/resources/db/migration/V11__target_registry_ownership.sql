-- Preserve every historical target definition and binding. An ID or matching
-- configuration value does not establish ownership of an existing row.
ALTER TABLE targets ADD COLUMN registry_owner VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNCLASSIFIED';
ALTER TABLE targets ADD COLUMN registry_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE targets ADD CONSTRAINT target_registry_owner_allowed
    CHECK (registry_owner IN ('LEGACY_UNCLASSIFIED', 'CONFIGURATION'));
ALTER TABLE targets ADD CONSTRAINT target_registry_revision_nonnegative
    CHECK (registry_revision >= 0);
