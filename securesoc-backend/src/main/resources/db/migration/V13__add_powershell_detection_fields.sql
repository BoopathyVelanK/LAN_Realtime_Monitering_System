-- F6: PowerShell Detection
--
-- Adds the two nullable columns PowerShellDetector's POWERSHELL_MATCH rule
-- type needs, on top of the existing running-app/detection-rule tables:
--   - running_apps.command_line   - raw per-process command line telemetry
--                                    (TEXT: an -EncodedCommand payload can
--                                    run to several thousand characters;
--                                    truncating it could break substring
--                                    matching against its tail)
--   - detection_rules.command_pattern - the admin-configured, case-insensitive
--                                    substring to match against command_line
--                                    (bounded VARCHAR, like the existing
--                                    process_name column added in V12)
--
-- Both columns are nullable and additive only - no backfill, no data
-- migration, no changes to any existing row or constraint. Older agents
-- that do not yet send a command line simply leave running_apps.command_line
-- NULL, and PowerShellDetector's repository query never matches a NULL
-- commandLine (see RunningAppRepository's Javadoc).

ALTER TABLE running_apps
    ADD COLUMN command_line TEXT;

ALTER TABLE detection_rules
    ADD COLUMN command_pattern VARCHAR(500);
