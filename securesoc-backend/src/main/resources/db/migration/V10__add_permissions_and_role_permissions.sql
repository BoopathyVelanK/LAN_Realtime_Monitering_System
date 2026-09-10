-- SecureSOC RBAC Phase 1: canonical permission vocabulary + role_permissions.
--
-- Scope of this migration deliberately stops at ADMIN and FACULTY, the two
-- roles the canonical RBAC model actually defines. LAB_ASSISTANT and AUDITOR
-- (seeded in V2__seed_roles.sql) intentionally receive ZERO permission rows
-- here — fail-closed by default — until their scope is confirmed. Adding
-- their mappings later is a pure INSERT, no schema change required.

CREATE TABLE permissions (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name  VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

INSERT INTO permissions (name) VALUES
    ('DASHBOARD_VIEW'),
    ('ENDPOINT_VIEW'),
    ('ENDPOINT_MANAGE'),
    ('APPLICATION_VIEW'),
    ('USB_VIEW'),
    ('VPN_VIEW'),
    ('IDLE_VIEW'),
    ('NETWORK_VIEW'),
    ('STUDENT_ACTIVITY_VIEW'),
    ('ALERT_VIEW'),
    ('ALERT_MANAGE'),
    ('RISK_VIEW'),
    ('DETECTION_RULE_VIEW'),
    ('DETECTION_RULE_MANAGE'),
    ('IOC_VIEW'),
    ('IOC_MANAGE'),
    ('EXAM_MODE_VIEW'),
    ('EXAM_MODE_MANAGE'),
    ('REPORT_VIEW'),
    ('REPORT_GENERATE'),
    ('REPORT_EXPORT'),
    ('ANALYTICS_VIEW'),
    ('INVENTORY_VIEW'),
    ('INVENTORY_MANAGE'),
    ('DEPARTMENT_MANAGE'),
    ('LABORATORY_MANAGE'),
    ('USER_MANAGE'),
    ('ROLE_MANAGE'),
    ('AUDIT_VIEW'),
    ('BACKUP_MANAGE'),
    ('SYSTEM_SETTINGS_MANAGE');

-- ADMIN: every permission, unconditionally.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN';

-- FACULTY: the scoped subset from the approved design proposal. Every one
-- of these is enforced with server-side scope on top (FacultyScopeService,
-- Phase 3) — this table alone only answers "can this role ever call this
-- endpoint type", not "which rows".
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'FACULTY'
  AND p.name IN (
    'DASHBOARD_VIEW', 'ENDPOINT_VIEW', 'APPLICATION_VIEW', 'USB_VIEW',
    'VPN_VIEW', 'IDLE_VIEW', 'NETWORK_VIEW', 'STUDENT_ACTIVITY_VIEW',
    'ALERT_VIEW', 'ALERT_MANAGE', 'RISK_VIEW', 'REPORT_VIEW',
    'REPORT_GENERATE', 'REPORT_EXPORT', 'ANALYTICS_VIEW', 'INVENTORY_VIEW'
    -- EXAM_MODE_VIEW / EXAM_MODE_MANAGE deliberately omitted pending your
    -- confirmation of the exam-mode grant model (open question from the
    -- design proposal). Add in a follow-up INSERT once confirmed — no
    -- schema change needed.
  );
