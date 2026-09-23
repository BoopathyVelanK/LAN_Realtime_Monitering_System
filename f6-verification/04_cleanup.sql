-- =====================================================================
-- f6-verification/04_cleanup.sql
-- SecureSOC — F6 Runtime Cleanup (final)
--
-- STATUS: this cleanup has already been performed manually against
-- securesoc-postgres (all 5 F6 endpoints, both F6 labs, and f6.faculty
-- are confirmed gone as of this revision). This script is kept in the
-- repo as the reproducible source of truth for that cleanup, and is
-- safe to re-run: every DELETE below is scoped to exact UUIDs/username,
-- so re-running against an already-clean database deletes 0 rows and
-- changes nothing. A database backup (securesoc_before_f6_cleanup.sql)
-- was taken before the manual run and is NOT committed to this repo.
--
-- Removes ONLY the temporary F6 WebSocket-verification data:
--   - 5 temp endpoints (F6-Test-Endpoint-B, F6-WS-LAB-A/A2/B/B1)
--   - the temp faculty user f6.faculty
--   - the 2 temp labs (F6-LAB-A, F6-LAB-B)
--
-- Confirmed FK behavior this script relies on:
--   endpoint_devices.lab_id            -> laboratories(id)      ON DELETE SET NULL
--   faculty_assignments.laboratory_id  -> laboratories(id)      ON DELETE CASCADE
--   idle_events.endpoint_id            -> endpoint_devices(id)  ON DELETE CASCADE
--   internet_usage_events.endpoint_id  -> endpoint_devices(id)  ON DELETE CASCADE
--   login_events.endpoint_id           -> endpoint_devices(id)  ON DELETE CASCADE
--   logout_events.endpoint_id          -> endpoint_devices(id)  ON DELETE CASCADE
--   network_usage_events.endpoint_id   -> endpoint_devices(id)  ON DELETE CASCADE
--   risk_scores.endpoint_id            -> endpoint_devices(id)  ON DELETE CASCADE
--   running_app_snapshots.endpoint_id  -> endpoint_devices(id)  ON DELETE CASCADE
--   usb_events.endpoint_id             -> endpoint_devices(id)  ON DELETE CASCADE
--   vpn_events.endpoint_id             -> endpoint_devices(id)  ON DELETE CASCADE
--   alerts.endpoint_id                 -> endpoint_devices(id)  ON DELETE SET NULL
--
-- Schema confirmed live against securesoc-postgres before this script was
-- finalized: faculty_assignments(id, faculty_user_id, laboratory_id,
-- exam_mode_authorized, created_at); user_roles(user_id, role_id);
-- users has no direct role column, so user_roles must be cleared explicitly.
--
-- The f6.faculty user is resolved by username via a subquery everywhere
-- below, never a hardcoded UUID — its id was never independently
-- confirmed during live verification (unlike Boopathy's id, hostname,
-- and MAC, which were cross-checked and are safe to hardcode as guards).
--
-- Every endpoint/lab ID below is the exact UUID supplied for this cleanup — no pattern
-- matching (LIKE 'F6%') is used anywhere, so this can never touch a record
-- that wasn't explicitly named.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Pre-flight guard: refuse to proceed at all if Boopathy's endpoint row
-- is missing, or its id/hostname/MAC don't all match the record on file.
-- This must pass before a single DELETE runs.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_hostname text;
    v_mac text;
BEGIN
    SELECT hostname, mac_address INTO v_hostname, v_mac
    FROM endpoint_devices
    WHERE id = '2de8dfec-51c2-452d-95dc-a69033918767';

    IF v_mac IS NULL THEN
        RAISE EXCEPTION 'SAFETY ABORT: Boopathy endpoint (2de8dfec-...) not found. Refusing to proceed.';
    END IF;

    IF v_hostname <> 'Boopathy' THEN
        RAISE EXCEPTION 'SAFETY ABORT: hostname mismatch for id 2de8dfec-... (found %). Refusing to proceed.', v_hostname;
    END IF;

    IF v_mac <> '00:50:56:C0:00:08' THEN
        RAISE EXCEPTION 'SAFETY ABORT: Boopathy MAC mismatch (found %). Refusing to proceed.', v_mac;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Step 1 — Remove f6.faculty's lab assignment(s) explicitly.
-- (Belt-and-suspenders: deleting the labs in Step 5 would CASCADE these
-- anyway, but doing it here first means Steps 2–3 don't depend on that
-- cascade timing to delete the user row cleanly.)
--
-- Resolved by username, NOT a hardcoded UUID: the f6.faculty user id was
-- never independently confirmed against the live database (only
-- Boopathy's id/hostname/MAC were cross-verified), so it must not be
-- baked into a DELETE condition here.
-- ---------------------------------------------------------------------
DELETE FROM faculty_assignments
WHERE faculty_user_id = (SELECT id FROM users WHERE username = 'f6.faculty');

-- ---------------------------------------------------------------------
-- Step 2 — Remove f6.faculty's role link(s) from user_roles.
-- Confirmed live: users has no role column of its own, so this is
-- required before Step 3 can delete the user row. Resolved by username
-- for the same reason as Step 1.
-- ---------------------------------------------------------------------
DELETE FROM user_roles
WHERE user_id = (SELECT id FROM users WHERE username = 'f6.faculty');

-- ---------------------------------------------------------------------
-- Step 3 — Remove the temporary faculty user itself, by username only
-- (the id was never independently verified, so it is not used here).
-- ---------------------------------------------------------------------
DELETE FROM users
WHERE username = 'f6.faculty';

-- ---------------------------------------------------------------------
-- Step 4 — Remove the five temporary F6 endpoints.
-- CASCADE handles: idle_events, internet_usage_events, login_events,
-- logout_events, network_usage_events, risk_scores,
-- running_app_snapshots, usb_events, vpn_events.
-- SET NULL handles: alerts.endpoint_id (rows are kept, endpoint_id nulled).
-- ---------------------------------------------------------------------
DELETE FROM endpoint_devices
WHERE id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41', -- F6-Test-Endpoint-B
    '0cd8724d-de5e-4370-9628-4ca4d8acf4e4', -- F6-WS-LAB-A
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda', -- F6-WS-LAB-A2
    '17a5f58a-6675-4ff0-b13a-1f3c24146e77', -- F6-WS-LAB-B
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197'  -- F6-WS-LAB-B1
);

-- ---------------------------------------------------------------------
-- Step 5 — Remove the two temporary F6 labs.
-- CASCADE handles: any remaining faculty_assignments pointing at these labs.
-- SET NULL handles: endpoint_devices.lab_id for any endpoint still pointing
-- here — specifically this is what sets Boopathy.lab_id back to NULL,
-- since the original fixture never recorded what it was before.
-- ---------------------------------------------------------------------
DELETE FROM laboratories
WHERE id IN (
    '2cc8a3e6-d672-4af1-8833-49e289d3f97a', -- F6-LAB-A
    '64398e4c-87d4-499d-88fc-a42c23fec241'  -- F6-LAB-B
);

-- ---------------------------------------------------------------------
-- Step 6 — Defensive, idempotent normalization: ensure Boopathy's
-- lab_id is NULL. Deleting the F6 labs in Step 5 already does this via
-- ON DELETE SET NULL; this step only matters if Step 5 deleted 0 rows
-- (e.g. re-running after the labs are already gone) yet lab_id was
-- somehow left non-NULL. Scoped to Boopathy's exact id only — never a
-- broad UPDATE — and re-verifies id/hostname/MAC before touching the row.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_hostname text;
    v_mac text;
BEGIN
    SELECT hostname, mac_address INTO v_hostname, v_mac
    FROM endpoint_devices
    WHERE id = '2de8dfec-51c2-452d-95dc-a69033918767';

    IF v_hostname = 'Boopathy' AND v_mac = '00:50:56:C0:00:08' THEN
        UPDATE endpoint_devices
        SET lab_id = NULL
        WHERE id = '2de8dfec-51c2-452d-95dc-a69033918767'
          AND lab_id IS NOT NULL;
    ELSE
        RAISE EXCEPTION 'SAFETY ABORT: Boopathy identity mismatch before lab_id normalization. Rolling back.';
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Post-flight guard: Boopathy must still exist with the same id,
-- hostname, and MAC after every step above, with lab_id NULL. If this
-- fails, the whole transaction rolls back.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_hostname text;
    v_mac text;
    v_lab_id uuid;
BEGIN
    SELECT hostname, mac_address, lab_id INTO v_hostname, v_mac, v_lab_id
    FROM endpoint_devices
    WHERE id = '2de8dfec-51c2-452d-95dc-a69033918767';

    IF v_mac IS NULL THEN
        RAISE EXCEPTION 'SAFETY ABORT: Boopathy endpoint disappeared during cleanup. Rolling back.';
    END IF;

    IF v_hostname <> 'Boopathy' THEN
        RAISE EXCEPTION 'SAFETY ABORT: Boopathy hostname changed during cleanup (found %). Rolling back.', v_hostname;
    END IF;

    IF v_mac <> '00:50:56:C0:00:08' THEN
        RAISE EXCEPTION 'SAFETY ABORT: Boopathy MAC changed during cleanup (found %). Rolling back.', v_mac;
    END IF;

    IF v_lab_id IS NOT NULL THEN
        RAISE EXCEPTION 'SAFETY ABORT: Boopathy lab_id is not NULL after cleanup (found %). Rolling back.', v_lab_id;
    END IF;
END $$;

COMMIT;

-- After COMMIT, run f6-verification/05_verify_cleanup.sql.
-- Expected: Boopathy still present (same id/hostname/MAC), lab_id IS NULL
-- (documented consequence of the original fixture not recording the
-- prior lab — see project notes; do not guess/restore a value here).