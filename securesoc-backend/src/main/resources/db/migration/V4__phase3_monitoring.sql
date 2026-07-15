-- SecureSOC Phase 3: Core Monitoring
-- One table per event type the agent sends to /monitoring/** (see
-- securesoc-agent/agent.py's run_monitoring_cycle, send_login_event,
-- send_logout_event, and collector.py for the exact payload shapes this
-- mirrors). No detection/risk logic lives here - that's Phase 4.

-- ---------------------------------------------------------------------
-- Login / logout events (agent.py: send_login_event / send_logout_event)
-- ---------------------------------------------------------------------

CREATE TABLE login_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id   UUID         NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    os_username   VARCHAR(150) NOT NULL,
    session_id    VARCHAR(100),                 -- always NULL for now - agent.py sends None
    login_time    TIMESTAMPTZ  NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_events_endpoint_id ON login_events(endpoint_id);
CREATE INDEX idx_login_events_login_time ON login_events(login_time);

CREATE TABLE logout_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id   UUID         NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    os_username   VARCHAR(150) NOT NULL,
    session_id    VARCHAR(100),
    logout_time   TIMESTAMPTZ  NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_logout_events_endpoint_id ON logout_events(endpoint_id);
CREATE INDEX idx_logout_events_logout_time ON logout_events(logout_time);

-- ---------------------------------------------------------------------
-- Running applications (collector.get_running_applications - process
-- snapshot per monitoring cycle; windowTitle is null until pywin32 is
-- wired up on real Windows hardware, see collector.py's docstring)
-- ---------------------------------------------------------------------

CREATE TABLE running_app_snapshots (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id   UUID         NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    captured_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_running_app_snapshots_endpoint_id ON running_app_snapshots(endpoint_id);
CREATE INDEX idx_running_app_snapshots_captured_at ON running_app_snapshots(captured_at);

-- One row per process in a snapshot, rather than a JSON blob, so future
-- detection rules (Phase 4) can filter/aggregate by process name in SQL.
CREATE TABLE running_apps (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id   UUID         NOT NULL REFERENCES running_app_snapshots(id) ON DELETE CASCADE,
    process_name  VARCHAR(255),
    window_title  VARCHAR(500),
    pid           INTEGER
);

CREATE INDEX idx_running_apps_snapshot_id ON running_apps(snapshot_id);
CREATE INDEX idx_running_apps_process_name ON running_apps(process_name);

-- ---------------------------------------------------------------------
-- USB device events (collector.get_usb_devices_stub - ingest endpoint
-- ready now, real WMI-based events land once the agent runs on real
-- Windows hardware; see collector.py's docstring)
-- ---------------------------------------------------------------------

CREATE TABLE usb_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id    UUID         NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    device_name    VARCHAR(255),
    device_id      VARCHAR(255),
    vendor_id      VARCHAR(20),
    product_id     VARCHAR(20),
    action         VARCHAR(20),   -- CONNECTED | DISCONNECTED
    event_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_usb_events_endpoint_id ON usb_events(endpoint_id);
CREATE INDEX idx_usb_events_event_time ON usb_events(event_time);

-- ---------------------------------------------------------------------
-- VPN adapter status (collector.detect_vpn_adapters - one row per
-- adapter found, or a single active=false row when none are)
-- ---------------------------------------------------------------------

CREATE TABLE vpn_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id    UUID         NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    adapter_name   VARCHAR(150),
    active         BOOLEAN      NOT NULL DEFAULT false,
    detected_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_vpn_events_endpoint_id ON vpn_events(endpoint_id);
CREATE INDEX idx_vpn_events_detected_at ON vpn_events(detected_at);

-- ---------------------------------------------------------------------
-- Idle time (collector.get_idle_seconds - endpoint ready now, real value
-- pending Windows GetLastInputInfo wiring)
-- ---------------------------------------------------------------------

CREATE TABLE idle_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id    UUID         NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    idle_seconds   INTEGER      NOT NULL,
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_idle_events_endpoint_id ON idle_events(endpoint_id);
CREATE INDEX idx_idle_events_recorded_at ON idle_events(recorded_at);

-- ---------------------------------------------------------------------
-- Network usage (collector.NetworkUsageTracker - raw byte deltas per
-- cycle; interfaceName always NULL for now, aggregated across interfaces)
-- ---------------------------------------------------------------------

CREATE TABLE network_usage_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id     UUID         NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    bytes_sent      BIGINT       NOT NULL DEFAULT 0,
    bytes_received  BIGINT       NOT NULL DEFAULT 0,
    interface_name  VARCHAR(100),
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_network_usage_events_endpoint_id ON network_usage_events(endpoint_id);
CREATE INDEX idx_network_usage_events_recorded_at ON network_usage_events(recorded_at);

-- ---------------------------------------------------------------------
-- Internet usage (same delta sample as network usage, expressed in MB
-- with the sampling window length - collector.py sends both from one
-- NetworkUsageTracker.sample() call as two separate POSTs)
-- ---------------------------------------------------------------------

CREATE TABLE internet_usage_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id     UUID          NOT NULL REFERENCES endpoint_devices(id) ON DELETE CASCADE,
    upload_mb       NUMERIC(12,3) NOT NULL DEFAULT 0,
    download_mb     NUMERIC(12,3) NOT NULL DEFAULT 0,
    period_seconds  INTEGER       NOT NULL DEFAULT 0,
    recorded_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_internet_usage_events_endpoint_id ON internet_usage_events(endpoint_id);
CREATE INDEX idx_internet_usage_events_recorded_at ON internet_usage_events(recorded_at);
