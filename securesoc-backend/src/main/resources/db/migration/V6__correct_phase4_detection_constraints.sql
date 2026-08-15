-- ---------------------------------------------------------------------
-- Phase 4 schema corrections.
--
-- V5 has already been applied, so these corrections must be additive
-- and belong in a new Flyway migration.
-- ---------------------------------------------------------------------

-- Preserve historical alerts when an endpoint is deleted.
ALTER TABLE alerts
    DROP CONSTRAINT alerts_endpoint_id_fkey;

ALTER TABLE alerts
    ADD CONSTRAINT alerts_endpoint_id_fkey
    FOREIGN KEY (endpoint_id)
    REFERENCES endpoint_devices(id)
    ON DELETE SET NULL;


-- Preserve authentication-failure history when a user is deleted.
-- The user_id becomes NULL, while source_ip and attempted_at remain.
ALTER TABLE auth_failure_events
    DROP CONSTRAINT auth_failure_events_user_id_fkey;

ALTER TABLE auth_failure_events
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE auth_failure_events
    ADD CONSTRAINT auth_failure_events_user_id_fkey
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE SET NULL;


-- Prevent duplicate detection rule definitions.
ALTER TABLE detection_rules
    ADD CONSTRAINT uq_detection_rules_name UNIQUE (name);