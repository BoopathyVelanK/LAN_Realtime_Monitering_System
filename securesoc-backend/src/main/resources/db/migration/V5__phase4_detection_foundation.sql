-- SecureSOC Phase 4: Detection Engine foundation (schema only)
-- No detection/alert/risk LOGIC lands in this phase - just the tables the
-- engine will read from and write to once it exists. See DetectionRule,
-- Alert, RiskScore, AuthFailureEvent entities for the Java side.

-- ---------------------------------------------------------------------
-- Detection rules (threshold-based only for now; rule_type leaves room
-- for SIGMA/IOC/CORRELATION kinds later without a schema change)
-- ---------------------------------------------------------------------

CREATE TABLE detection_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    rule_type       VARCHAR(30)  NOT NULL DEFAULT 'THRESHOLD',
    event_source    VARCHAR(50)  NOT NULL,     -- e.g. 'AUTH_FAILURE', 'USB_EVENT'
    threshold       INTEGER,
    window_seconds  INTEGER,
    severity        VARCHAR(20)  NOT NULL,     -- LOW | MEDIUM | HIGH | CRITICAL
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_detection_rules_event_source ON detection_rules(event_source);
CREATE INDEX idx_detection_rules_enabled ON detection_rules(enabled);

-- ---------------------------------------------------------------------
-- Alerts (produced by the future Alert Engine from detection rule hits;
-- endpoint_id/rule_id are nullable since not every future alert source
-- will necessarily be endpoint-scoped or rule-based)
-- ---------------------------------------------------------------------

CREATE TABLE alerts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id      UUID         REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    rule_id          UUID         REFERENCES detection_rules(id) ON DELETE SET NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    severity         VARCHAR(20)  NOT NULL,               -- LOW | MEDIUM | HIGH | CRITICAL
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN', -- OPEN | ACKNOWLEDGED | RESOLVED
    acknowledged_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    acknowledged_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_alerts_endpoint_id ON alerts(endpoint_id);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_created_at ON alerts(created_at);

-- ---------------------------------------------------------------------
-- Risk scores (one live row per endpoint; the future Risk Engine
-- overwrites this row as new signals come in - history is a later table,
-- not this one, so its shape doesn't get guessed at prematurely)
-- ---------------------------------------------------------------------

CREATE TABLE risk_scores (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id   UUID     NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    score         SMALLINT NOT NULL DEFAULT 0 CHECK (score BETWEEN 0 AND 100),
    level         VARCHAR(20) NOT NULL DEFAULT 'SAFE', -- SAFE|LOW|MEDIUM|HIGH|CRITICAL
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One live score row per endpoint - also gives the future Risk Engine a
-- natural UPSERT target (ON CONFLICT (endpoint_id) DO UPDATE ...).
CREATE UNIQUE INDEX idx_risk_scores_endpoint_id ON risk_scores(endpoint_id);

-- ---------------------------------------------------------------------
-- Auth failure events (NEW telemetry - individual failed portal login
-- attempts. Previously only a running counter existed on users.
-- failed_login_attempts/locked_until; that counter-based lockout logic in
-- AuthService is UNCHANGED by this migration. This table is purely
-- additive: one row per failed attempt, so a future rule engine can query
-- "N failures in a time window" per user, which a bare counter can't
-- answer once it's been reset by a subsequent successful login.)
-- ---------------------------------------------------------------------

CREATE TABLE auth_failure_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_ip     VARCHAR(45),                 -- IPv4 or IPv6, best-effort (see AuthController)
    attempted_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_failure_events_user_id ON auth_failure_events(user_id);
CREATE INDEX idx_auth_failure_events_attempted_at ON auth_failure_events(attempted_at);
