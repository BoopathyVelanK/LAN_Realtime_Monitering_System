-- ---------------------------------------------------------------------
-- SecureSOC Phase 4: per-user alert deduplication support.
--
-- V1-V6 are already applied and must not be modified.
-- This migration adds the user identity to user-scoped alerts so
-- authentication alerts can be deduplicated independently per user.
-- ---------------------------------------------------------------------

ALTER TABLE alerts
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_alerts_user_id
    ON alerts(user_id);
