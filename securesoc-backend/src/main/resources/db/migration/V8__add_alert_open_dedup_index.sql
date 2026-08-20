-- ---------------------------------------------------------------------
-- SecureSOC Phase 4: alert deduplication - database-level invariant.
--
-- V1-V7 are already applied and must not be modified.
--
-- AlertService.createAlertFrom performs an application-level
-- check-then-insert (look for an existing OPEN alert for the same
-- (user_id, rule_id), reuse it if found) before creating a new Alert.
-- That check is only an optimization - by itself it cannot prevent two
-- concurrent requests from both passing the check and both inserting.
-- This partial unique index is the actual source of truth for the
-- invariant: at most one OPEN alert may exist for a given non-null
-- user_id + rule_id pair.
--
-- endpoint_id is deliberately NOT part of this key - deduplication is
-- scoped to (user_id, rule_id) only. A user-scoped alert (e.g. repeated
-- portal login failures) can have a null endpoint_id, so keying on
-- endpoint_id as well would incorrectly allow duplicate OPEN alerts for
-- the same user/rule whenever endpoint_id differs (or is null).
--
-- The index is partial (WHERE status = 'OPEN' AND user_id IS NOT NULL)
-- so that:
--   - ACKNOWLEDGED/RESOLVED alerts are never subject to this constraint -
--     a new OPEN alert for the same user/rule can always be created once
--     the prior one has been acknowledged or resolved.
--   - Alerts with no associated user (user_id IS NULL - e.g.
--     endpoint-only detections) are never subject to this constraint,
--     matching AlertService's "skip deduplication when userId is null"
--     behavior.
-- ---------------------------------------------------------------------

CREATE UNIQUE INDEX idx_alerts_open_user_rule_dedup
    ON alerts(user_id, rule_id)
    WHERE status = 'OPEN'
      AND user_id IS NOT NULL;
