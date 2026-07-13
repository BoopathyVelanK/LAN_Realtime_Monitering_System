-- Default admin account so there's a working login on a freshly migrated
-- database, matching the frontend login page's placeholder
-- "soc.admin@securesoc". CHANGE THIS PASSWORD IMMEDIATELY after first
-- login in any non-throwaway environment - it is public in source control.
--
-- Username: soc.admin
-- Email:    soc.admin@securesoc.local
-- Password: ChangeMe123!   (bcrypt hash below)

INSERT INTO users (username, email, password_hash, full_name, enabled)
VALUES (
    'soc.admin',
    'soc.admin@securesoc.local',
    '$2b$10$Ce6Dh13DY5bG/XdOms3l5eCvcHDuSVox1/z/OUcNutaVUQhR1uByi',
    'SecureSOC Administrator',
    true
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'soc.admin' AND r.name = 'ADMIN';
