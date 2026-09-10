-- SecureSOC RBAC Phase 2: Student entity + Faculty->Laboratory assignment.
--
-- Design decisions locked in for this migration:
--   - Faculty scope is laboratory-grain ONLY. No department-level grant
--     (deferred), no Class/Section concept (SRS defines none).
--   - Student is a separate entity, not a User role — students never
--     authenticate into SecureSOC.
--   - Exam Mode is NOT bundled into the blanket FACULTY role_permissions
--     grant (V10 deliberately excluded EXAM_MODE_VIEW/MANAGE for this
--     reason). It is instead an explicit per-assignment authorization:
--     a Faculty member only gets exam-mode control for a given lab if
--     that specific faculty_assignments row has exam_mode_authorized =
--     true. Both the EXAM_MODE_VIEW/MANAGE permission AND this per-row
--     flag are required together — neither is sufficient alone.

CREATE TABLE students (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_code  VARCHAR(50) NOT NULL UNIQUE,
    full_name     VARCHAR(150) NOT NULL,
    department_id UUID REFERENCES departments(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE faculty_assignments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    laboratory_id         UUID NOT NULL REFERENCES laboratories(id) ON DELETE CASCADE,
    exam_mode_authorized  BOOLEAN NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_faculty_assignment UNIQUE (faculty_user_id, laboratory_id)
);

CREATE INDEX idx_faculty_assignments_faculty_user_id ON faculty_assignments(faculty_user_id);
CREATE INDEX idx_faculty_assignments_laboratory_id ON faculty_assignments(laboratory_id);

-- Endpoint <-> Student: "one endpoint can have one assigned student" (SRS).
-- Nullable and additive — every existing endpoint_devices row is
-- unaffected until an admin assigns a student to it.
ALTER TABLE endpoint_devices
    ADD COLUMN assigned_student_id UUID REFERENCES students(id) ON DELETE SET NULL;

CREATE INDEX idx_endpoint_devices_assigned_student_id ON endpoint_devices(assigned_student_id);
