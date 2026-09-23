BEGIN;

INSERT INTO laboratories (id, name, code, capacity)
SELECT gen_random_uuid(), 'F6 Runtime Lab A', 'F6-LAB-A', 30
WHERE NOT EXISTS (SELECT 1 FROM laboratories WHERE code = 'F6-LAB-A');

INSERT INTO laboratories (id, name, code, capacity)
SELECT gen_random_uuid(), 'F6 Runtime Lab B', 'F6-LAB-B', 30
WHERE NOT EXISTS (SELECT 1 FROM laboratories WHERE code = 'F6-LAB-B');

-- password_hash reused directly from soc.admin via subquery - never typed literally
INSERT INTO users (id, username, email, password_hash, full_name, enabled)
SELECT gen_random_uuid(), 'f6.faculty', 'f6.faculty@securesoc.local',
       (SELECT password_hash FROM users WHERE username = 'soc.admin'),
       'F6 Faculty Test User', true
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'f6.faculty');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'f6.faculty' AND r.name = 'FACULTY'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur
    JOIN users u2 ON u2.id = ur.user_id
    JOIN roles r2 ON r2.id = ur.role_id
    WHERE u2.username = 'f6.faculty' AND r2.name = 'FACULTY'
  );

INSERT INTO faculty_assignments (id, faculty_user_id, laboratory_id, exam_mode_authorized)
SELECT gen_random_uuid(), u.id, l.id, false
FROM users u, laboratories l
WHERE u.username = 'f6.faculty' AND l.code = 'F6-LAB-A'
  AND NOT EXISTS (
    SELECT 1 FROM faculty_assignments fa
    JOIN users u2 ON u2.id = fa.faculty_user_id
    JOIN laboratories l2 ON l2.id = fa.laboratory_id
    WHERE u2.username = 'f6.faculty' AND l2.code = 'F6-LAB-A'
  );

-- reversible - cleanup script sets this back to NULL
UPDATE endpoint_devices
SET lab_id = (SELECT id FROM laboratories WHERE code = 'F6-LAB-A')
WHERE id = '2de8dfec-51c2-452d-95dc-a69033918767';

INSERT INTO endpoint_devices (
    id, hostname, mac_address, ip_address, lab_id,
    os_name, os_version, cpu_info, ram_mb, disk_gb,
    agent_version, agent_token_hash, status
)
SELECT gen_random_uuid(), 'F6-Test-Endpoint-B', '02:00:00:F6:00:02', '10.250.6.2',
       (SELECT id FROM laboratories WHERE code = 'F6-LAB-B'),
       'Windows', 'F6-Test', 'F6-Test', 4096, 50,
       'F6-TEST', 'f6-test-endpoint-b-token-hash', 'OFFLINE'
WHERE NOT EXISTS (SELECT 1 FROM endpoint_devices WHERE mac_address = '02:00:00:F6:00:02');

COMMIT;
