-- =====================================================================
-- f6-verification/05_verify_cleanup.sql
-- SecureSOC — F6 Runtime Cleanup verification (final)
--
-- Run any time after 04_cleanup.sql has been applied (manually or via
-- the script). Read-only — no INSERT/UPDATE/DELETE anywhere in this file.
-- Every "count" check below should read 0 unless noted otherwise.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. F6 laboratories = 0
-- ---------------------------------------------------------------------
SELECT 'f6_labs_remaining' AS check_name, count(*) AS result
FROM laboratories
WHERE id IN (
    '2cc8a3e6-d672-4af1-8833-49e289d3f97a', -- F6-LAB-A
    '64398e4c-87d4-499d-88fc-a42c23fec241'  -- F6-LAB-B
) OR code IN ('F6-LAB-A', 'F6-LAB-B');

-- ---------------------------------------------------------------------
-- 2. All five known F6 endpoint IDs = 0
-- ---------------------------------------------------------------------
SELECT 'f6_endpoints_remaining' AS check_name, count(*) AS result
FROM endpoint_devices
WHERE id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41', -- F6-Test-Endpoint-B
    '0cd8724d-de5e-4370-9628-4ca4d8acf4e4', -- F6-WS-LAB-A
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda', -- F6-WS-LAB-A2
    '17a5f58a-6675-4ff0-b13a-1f3c24146e77', -- F6-WS-LAB-B
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197'  -- F6-WS-LAB-B1
);

-- ---------------------------------------------------------------------
-- 3. No stray F6 MAC-prefixed endpoints (informational scan, not used
--    for deletion anywhere — read-only check that nothing F6-tagged
--    slipped past the exact-ID list above)
-- ---------------------------------------------------------------------
SELECT 'f6_mac_prefix_endpoints_remaining' AS check_name, count(*) AS result
FROM endpoint_devices
WHERE mac_address LIKE '02:00:00:F6:%';

-- ---------------------------------------------------------------------
-- 4. f6.faculty user = 0
-- Resolved by username, not a hardcoded UUID (id was never
-- independently confirmed during live verification).
-- ---------------------------------------------------------------------
SELECT 'f6_faculty_user_remaining' AS check_name, count(*) AS result
FROM users
WHERE username = 'f6.faculty';

-- 4b. f6.faculty's assignments and role links = 0
SELECT 'f6_faculty_assignments_remaining' AS check_name, count(*) AS result
FROM faculty_assignments
WHERE faculty_user_id = (SELECT id FROM users WHERE username = 'f6.faculty')
   OR laboratory_id IN (
        '2cc8a3e6-d672-4af1-8833-49e289d3f97a',
        '64398e4c-87d4-499d-88fc-a42c23fec241'
   );

SELECT 'f6_faculty_user_roles_remaining' AS check_name, count(*) AS result
FROM user_roles
WHERE user_id = (SELECT id FROM users WHERE username = 'f6.faculty');

-- ---------------------------------------------------------------------
-- 5. Zero remaining telemetry for the five deleted endpoint IDs across
--    all nine dependent tables
-- ---------------------------------------------------------------------
SELECT 'idle_events' AS table_name, count(*) AS result FROM idle_events WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197')
UNION ALL
SELECT 'internet_usage_events', count(*) FROM internet_usage_events WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197')
UNION ALL
SELECT 'login_events', count(*) FROM login_events WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197')
UNION ALL
SELECT 'logout_events', count(*) FROM logout_events WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197')
UNION ALL
SELECT 'network_usage_events', count(*) FROM network_usage_events WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197')
UNION ALL
SELECT 'risk_scores', count(*) FROM risk_scores WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197')
UNION ALL
SELECT 'running_app_snapshots', count(*) FROM running_app_snapshots WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197')
UNION ALL
SELECT 'usb_events', count(*) FROM usb_events WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197')
UNION ALL
SELECT 'vpn_events', count(*) FROM vpn_events WHERE endpoint_id IN (
    '940d1c2b-c375-43ec-8fac-8eb8ece45a41','0cd8724d-de5e-4370-9628-4ca4d8acf4e4',
    'c2b28ff5-a5d6-456f-816f-a0ccd4c36cda','17a5f58a-6675-4ff0-b13a-1f3c24146e77',
    'a6888aa4-6ab9-4015-8089-b87b9c8fb197');
-- Expect: every row's result = 0

-- ---------------------------------------------------------------------
-- 7. Boopathy still exists with expected id/hostname/MAC, lab_id NULL
-- ---------------------------------------------------------------------
SELECT id, hostname, mac_address, lab_id
FROM endpoint_devices
WHERE id = '2de8dfec-51c2-452d-95dc-a69033918767';
-- Expect: exactly 1 row —
--   hostname     = 'Boopathy'
--   mac_address  = '00:50:56:C0:00:08'
--   lab_id       IS NULL
-- (lab_id is NULL because the original F6 fixture never recorded
-- Boopathy's pre-fixture lab — this is documented, not a bug; never
-- infer or restore a value here.)

-- ---------------------------------------------------------------------
-- 8. Spot-check total endpoint count (compare manually against your
--    last known non-F6 total to confirm no unrelated row was removed)
-- ---------------------------------------------------------------------
SELECT 'total_endpoints' AS check_name, count(*) AS result FROM endpoint_devices;