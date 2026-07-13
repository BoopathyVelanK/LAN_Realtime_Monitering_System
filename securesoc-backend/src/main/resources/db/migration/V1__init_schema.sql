-- SecureSOC core schema (Phase 1: Foundation)
-- UUID primary keys throughout so the frontend's string-typed `id` fields
-- (see frontend/src/types/api.ts) map directly with no translation layer.

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- for gen_random_uuid()

-- ---------------------------------------------------------------------
-- Organizational structure
-- ---------------------------------------------------------------------

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE laboratories (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(150) NOT NULL,
    code           VARCHAR(20)  NOT NULL UNIQUE,
    department_id  UUID         REFERENCES departments(id) ON DELETE SET NULL,
    capacity       INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_laboratories_department_id ON laboratories(department_id);

-- ---------------------------------------------------------------------
-- Auth: roles, users, refresh tokens
-- ---------------------------------------------------------------------

CREATE TABLE roles (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name  VARCHAR(50) NOT NULL UNIQUE  -- e.g. ADMIN, FACULTY, LAB_ASSISTANT, AUDITOR
);

CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username       VARCHAR(100) NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,          -- BCrypt
    full_name      VARCHAR(150) NOT NULL,
    department_id  UUID         REFERENCES departments(id) ON DELETE SET NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT true,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until   TIMESTAMPTZ,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id  UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Refresh tokens are stored hashed (SHA-256), never in plaintext, so a DB
-- read alone can't be replayed as a live session token. One row per issued
-- token; revoked/rotated on refresh (rotation-on-use) and on logout.
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE, -- hex SHA-256
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- ---------------------------------------------------------------------
-- Endpoint devices (agents register here; Phase 2/3 monitoring tables
-- for events/alerts/risk land in a later migration once that phase starts)
-- ---------------------------------------------------------------------

CREATE TABLE endpoint_devices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hostname          VARCHAR(150) NOT NULL,
    mac_address       VARCHAR(17)  NOT NULL UNIQUE,  -- AA:BB:CC:DD:EE:FF
    ip_address        VARCHAR(45),                    -- IPv4 or IPv6
    lab_id            UUID         REFERENCES laboratories(id) ON DELETE SET NULL,
    os_name           VARCHAR(100),
    os_version        VARCHAR(100),
    cpu_info          VARCHAR(150),
    ram_mb            INTEGER,
    disk_gb           INTEGER,
    agent_version     VARCHAR(50),
    -- Agent auth: a per-device opaque token, stored hashed like refresh
    -- tokens. Issued once at registration; agent.py persists the raw
    -- value locally (encrypted, see state_crypto.py) and never re-sends it
    -- in plaintext except as the X-Agent-Token header over TLS.
    agent_token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE', -- ONLINE | OFFLINE
    last_heartbeat_at TIMESTAMPTZ,
    registered_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_endpoint_devices_lab_id ON endpoint_devices(lab_id);
CREATE INDEX idx_endpoint_devices_status ON endpoint_devices(status);
