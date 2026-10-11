-- SecureSOC: endpoint-only OPEN alert deduplication.
--
-- Preserve historical alert records. For each endpoint/rule pair,
-- retain the newest OPEN alert and mark older duplicate OPEN alerts
-- SUPERSEDED rather than deleting them or falsely acknowledging them.
--
-- User-scoped alerts are excluded; their deduplication remains governed
-- by the existing V8 partial unique index.

WITH ranked_endpoint_alerts AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY endpoint_id, rule_id
            ORDER BY created_at DESC, id DESC
        ) AS row_num
    FROM alerts
    WHERE status = 'OPEN'
      AND user_id IS NULL
      AND endpoint_id IS NOT NULL
      AND rule_id IS NOT NULL
)
UPDATE alerts AS a
SET status = 'SUPERSEDED'
FROM ranked_endpoint_alerts AS ranked
WHERE a.id = ranked.id
  AND ranked.row_num > 1;

-- Enforce one OPEN endpoint-only alert per endpoint/rule pair.
CREATE UNIQUE INDEX idx_alerts_open_endpoint_rule_dedup
    ON alerts(endpoint_id, rule_id)
    WHERE status = 'OPEN'
      AND user_id IS NULL
      AND endpoint_id IS NOT NULL
      AND rule_id IS NOT NULL;
