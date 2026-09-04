-- Historical plans remain readable. They require replanning before approval/apply:
-- no trustworthy context can be retroactively inferred from legacy records.
ALTER TABLE change_records ADD COLUMN policy_revision VARCHAR(128);
ALTER TABLE change_records ADD COLUMN target_context_fingerprint VARCHAR(128);
ALTER TABLE change_records ADD COLUMN integrity_fingerprint VARCHAR(128);
-- Preserve authenticated issuer/subject identities without truncation.
ALTER TABLE change_records ALTER COLUMN actor TYPE TEXT;
ALTER TABLE change_records ALTER COLUMN approved_by TYPE TEXT;
ALTER TABLE change_records ALTER COLUMN rejected_by TYPE TEXT;
