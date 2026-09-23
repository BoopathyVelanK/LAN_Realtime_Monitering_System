SELECT 'lab' AS kind, code, id::text FROM laboratories WHERE code IN ('F6-LAB-A','F6-LAB-B')
UNION ALL
SELECT 'faculty', username, id::text FROM users WHERE username = 'f6.faculty'
UNION ALL
SELECT 'endpoint', hostname, id::text FROM endpoint_devices
WHERE id = '2de8dfec-51c2-452d-95dc-a69033918767' OR hostname = 'F6-Test-Endpoint-B'
ORDER BY kind, code;

-- structural check
SELECT u.username AS faculty, r.name AS role, l.code AS assigned_lab, fa.exam_mode_authorized
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
LEFT JOIN faculty_assignments fa ON fa.faculty_user_id = u.id
LEFT JOIN laboratories l ON l.id = fa.laboratory_id
WHERE u.username = 'f6.faculty';

SELECT hostname, lab_id, status FROM endpoint_devices
WHERE id = '2de8dfec-51c2-452d-95dc-a69033918767' OR hostname = 'F6-Test-Endpoint-B';
