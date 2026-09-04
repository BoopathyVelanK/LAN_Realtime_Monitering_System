-- Adds the agent's own collection timestamp (sampledAt) alongside the
-- existing recorded_at (backend ingestion time, unchanged) for both
-- network and internet usage events.
--
-- Nullable, no default, no backfill:
--   - Historical rows predate this column and have no agent-supplied
--     collection time to backfill from - NULL is the honest value for
--     them, not a synthesized guess.
--   - A not-yet-upgraded agent, or a monitoring payload already sitting
--     in an agent's offline queue from before this change, also won't
--     include sampledAt - MonitoringService persists whatever the
--     request carries (including null) rather than substituting
--     Instant.now(), which would make it indistinguishable from
--     recorded_at.
--
-- No new index: neither NetworkUsageEventRepository nor
-- InternetUsageEventRepository queries by sampled_at today (both only
-- order/filter by recorded_at + endpoint_id, already indexed). Adding an
-- index with no query to justify it would be premature - revisit once a
-- future aggregation/chart phase actually queries this column.

ALTER TABLE network_usage_events
    ADD COLUMN sampled_at TIMESTAMPTZ;

ALTER TABLE internet_usage_events
    ADD COLUMN sampled_at TIMESTAMPTZ;
