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
    event_source    VARCHAR(50)  NOT NULL,
    threshold       INTEGER,
    window_seconds  INTEGER,
    severity        VARCHAR(20)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_detection_rules_name UNIQUE (name)
);

CREATE INDEX idx_detection_rules_event_source
    ON detection_rules(event_source);

CREATE INDEX idx_detection_rules_enabled
    ON detection_rules(enabled);

-- ---------------------------------------------------------------------
-- Alerts (produced by the future Alert Engine from detection rule hits)
-- endpoint_id/rule_id are nullable since not every future alert source
-- will necessarily be endpoint-scoped or rule-based.
-- ---------------------------------------------------------------------

CREATE TABLE alerts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id      UUID REFERENCES endpoint_devices(id) ON DELETE SET NULL,
    rule_id           UUID REFERENCES detection_rules(id) ON DELETE SET NULL,
    title             VARCHAR(200) NOT NULL,
    description      TEXT,
    severity         VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    acknowledged_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    acknowledged_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alerts_endpoint_id
    ON alerts(endpoint_id);

CREATE INDEX idx_alerts_status
    ON alerts(status);

CREATE INDEX idx_alerts_created_at
    ON alerts(created_at);

-- ---------------------------------------------------------------------
-- Risk scores (one live row per endpoint; the future Risk Engine
-- overwrites this row as new signals come in).
--
-- endpoint_id remains NOT NULL because a live risk score must belong
-- to an existing endpoint. If the endpoint is deleted, its live risk
-- score is deleted as well.
-- ---------------------------------------------------------------------

CREATE TABLE risk_scores (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id   UUID NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    score         SMALLINT NOT NULL DEFAULT 0
                  CHECK (score BETWEEN 0 AND 100),
    level         VARCHAR(20) NOT NULL DEFAULT 'SAFE',
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One live score row per endpoint.
CREATE UNIQUE INDEX idx_risk_scores_endpoint_id
    ON risk_scores(endpoint_id);

-- ---------------------------------------------------------------------
-- Auth failure events (individual failed portal login attempts).
--
-- This is separate from login_events, which represent endpoint login
-- telemetry from the Python agent.
--
-- user_id is nullable because authentication history must survive
-- deletion of the associated user. When a user is deleted, user_id
-- becomes NULL while source_ip and attempted_at remain available.
-- ---------------------------------------------------------------------

CREATE TABLE auth_failure_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    source_ip     VARCHAR(45),
    attempted_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_failure_events_user_id
    ON auth_failure_events(user_id);

CREATE INDEX idx_auth_failure_events_attempted_at
    ON auth_failure_events(attempted_at);
