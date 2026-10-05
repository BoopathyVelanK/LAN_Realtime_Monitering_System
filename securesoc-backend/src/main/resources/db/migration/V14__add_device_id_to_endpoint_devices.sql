-- Stable endpoint identity (Windows MachineGuid, sent by the agent as
-- "deviceId"). Nullable: rows registered by pre-existing agents have no
-- device_id until their agent re-registers with one. Existing rows are NOT
-- backfilled, merged or deleted by this migration.
--
-- A plain UNIQUE index is sufficient: PostgreSQL treats NULLs as distinct,
-- so any number of legacy rows may keep device_id = NULL while two rows can
-- never share the same non-NULL device_id.
ALTER TABLE endpoint_devices ADD COLUMN device_id VARCHAR(64);

CREATE UNIQUE INDEX uq_endpoint_devices_device_id ON endpoint_devices (device_id);
