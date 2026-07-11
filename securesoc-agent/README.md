# SecureSOC Windows Endpoint Agent — Phase 2

Lightweight Python collector that registers a machine with the SecureSOC backend and
sends periodic heartbeats. It makes **zero** security/business decisions — it only
collects and forwards, per the SRS.

## What it does

1. **On first run** — collects hostname, MAC address, IP, OS, CPU, RAM, disk, and
   `POST`s to `/api/agents/register` with a shared registration secret. The backend
   returns a per-device opaque token, which is saved to `agent_state.json` so the
   agent never re-registers (and never rotates its token) on restart.
2. **Every `heartbeat_interval_seconds`** — collects live CPU/RAM/disk usage % and
   `POST`s to `/api/agents/heartbeat` using that saved token. The backend marks the
   endpoint `ONLINE` and records the sample.
3. **If the backend rejects a heartbeat (401)** — token is invalid (e.g. backend DB
   was reset), so the agent clears its local state and re-registers automatically.
4. **If the backend is unreachable** — logs a warning and retries on the next cycle.
   (Full offline JSONL/SQLite buffering is a later-phase enhancement — see SRS Part 3;
   Phase 2 keeps this simple: skip and retry.)

## Setup

```bash
cd securesoc-agent
pip install -r requirements.txt
```

Edit `config.ini`:
```ini
[server]
url = http://localhost:8080/api
registration_secret = <same value as backend's AGENT_REGISTRATION_SECRET>
lab_id =    # optional — leave blank if unknown, admin assigns later
```

## Run

```bash
python agent.py
```

You should see something like:
```
2026-07-08 10:00:01 [INFO] SecureSOC agent starting. Server: http://localhost:8080/api
2026-07-08 10:00:01 [INFO] Registering with backend as hostname=LAB-PC-04 mac=AA:BB:CC:DD:EE:FF
2026-07-08 10:00:01 [INFO] Registered successfully. endpoint_id=... status=OFFLINE message=Registered. Endpoint is unassigned to a lab...
2026-07-08 10:00:16 [INFO] Heartbeat OK — status=ONLINE cpu=3.2% ram=41.5% disk=62.0%
```

Verify from the backend side (needs an admin/faculty JWT — see backend README for login):
```bash
curl http://localhost:8080/api/endpoints -H "Authorization: Bearer <accessToken>"
```
You should see your machine listed with `"status": "ONLINE"` and a recent `lastHeartbeatAt`.

**Stop the agent and wait ~60s (default `heartbeat-timeout-seconds`)** — the backend's
`EndpointOfflineSweeper` job will flip it back to `OFFLINE` automatically. Re-run
`GET /endpoints` to confirm. This proves the full "one endpoint checking in" milestone
from the Phase 2 plan.

## Files

```
agent.py          Main loop: registration + heartbeat scheduling
collector.py       Pure system-info gathering (psutil) — no business logic
config.ini          Server URL, registration secret, intervals
requirements.txt    psutil, requests
agent_state.json    Created automatically after first successful registration
agent.log           Rolling log file (also prints to stdout)
```

## Running as a Windows Service (later step)

Phase 2 runs the agent as a plain foreground script for testing. Turning this into an
auto-starting Windows Service (via `pywin32`, with auto-restart on failure) is part of
the agent's "Windows Service" requirement in the SRS — planned as a follow-up once the
core register/heartbeat loop is confirmed working end-to-end against your backend.

---

## Phase 3 — Core Monitoring additions

The agent now also sends, on a separate `monitoring_interval_seconds` cycle
(default 60s, configurable in `config.ini`):

- **Running applications** — full process snapshot via `psutil`
- **Network + internet usage** — real byte deltas via `psutil.net_io_counters()`
- **VPN adapter detection** — heuristic scan of network interface names
- **Idle time** — endpoint ready, collector currently returns `None` (Windows-only API needed)
- **Login/logout events** — sent once at agent startup/shutdown, tagged with OS username

See `securesoc-backend/README.md` → "Phase 3" section for the full honest breakdown of
what's fully real vs. what's a ready-to-wire stub pending real Windows hardware
(window titles, precise idle time, USB hotplug events all need `pywin32`).

---

## Reliability hardening (post-Phase 3)

Four targeted improvements on top of the original agent, none of which change the
registration/heartbeat/monitoring logic, payload formats, or APIs:

1. **Rotating logs** — `agent.log` now rotates at 10 MB with 10 backups kept
   (`RotatingFileHandler`), instead of growing unbounded.

2. **Startup configuration validation** (`validate_config()` in `agent.py`) — checks
   `[server] url`, `registration_secret`, and the three `[agent]` interval/timeout
   values *before* any network activity begins. Invalid config → clear itemized error
   message, clean non-zero exit, never a raw traceback. Valid config → starts exactly
   as before, silently.

3. **Per-collector fault isolation** (`run_monitoring_cycle()`) — each of the 4
   collectors (running apps, network/internet usage, VPN, idle) runs in its own
   try/except. If one throws, it's logged by name with the full exception, its data
   is simply omitted for that cycle, and every other collector still runs and still
   sends its data. One broken collector can no longer take down the whole agent.

4. **`agent_state.json` recovery** (`AgentState._load()`) — handles a missing file,
   corrupted JSON (preserved as `agent_state.corrupted.json` before re-registering),
   missing/invalid required fields, and I/O errors, each with a clear log message and
   automatic re-registration where recovery is possible. A genuinely valid existing
   state file still loads silently, exactly as before — full backward compatibility.
   Neither `endpointId` nor `agentToken` values are ever written to a log message.

### Config additions (config.ini — all backward compatible, fall back to these
### defaults if absent from an older config file)
```ini
[agent]
monitoring_interval_seconds = 60
http_timeout_seconds = 10
```
