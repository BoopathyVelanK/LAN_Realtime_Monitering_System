"""
agent.py — SecureSOC Windows Endpoint Agent (Phase 2)

Responsibilities (and ONLY these — see SRS Part 3):
  1. Register this machine with the Spring Boot backend once.
  2. Send periodic heartbeats with basic resource usage.
  3. Nothing else. No detection, no alerting, no policy decisions — that
     all lives in the Java backend.

Run:
    python agent.py

Stop:
    Ctrl+C
"""

import base64
import configparser
import json
import logging
import sys
import threading
import time
from datetime import datetime, timezone
from logging.handlers import RotatingFileHandler
from pathlib import Path
from urllib.parse import urlparse

import requests

import collector
import offline_queue
import state_crypto

CONFIG_PATH = Path(__file__).parent / "config.ini"


def load_config() -> configparser.ConfigParser:
    if not CONFIG_PATH.exists():
        sys.exit(f"Missing config file: {CONFIG_PATH}. Copy config.ini and fill in your values.")
    config = configparser.ConfigParser()
    config.read(CONFIG_PATH)
    return config


def setup_logging(level_name: str) -> logging.Logger:
    level = getattr(logging, level_name.upper(), logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(message)s",
        handlers=[
            logging.StreamHandler(sys.stdout),
            RotatingFileHandler("agent.log", maxBytes=10 * 1024 * 1024, backupCount=10),
        ],
    )
    return logging.getLogger("securesoc-agent")


# =====================================================================
# Configuration validation
# =====================================================================
# Single dedicated entry point for all config.ini validation — nothing
# elsewhere in the file re-validates these values. Runs in main() right
# after load_config() and BEFORE setup_logging()/SecureSocAgent are ever
# constructed, so a bad config is caught before any log file, HTTP
# session, or registration/heartbeat activity begins.

class ConfigValidationError(Exception):
    """Raised when config.ini fails validation. Always caught in main()
    and turned into a clean, non-zero exit — never an uncaught traceback."""


_VALID_LOG_LEVELS = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}
_PLACEHOLDER_SECRETS = {"changeme", "your_secret", "replace_me", "example"}
_MIN_SECRET_LENGTH = 32
_RECOMMENDED_MIN_POLL_SECONDS = 5


def _validate_positive_int(config: configparser.ConfigParser, section: str, key: str, label: str,
                            errors: list, logger: logging.Logger, recommended_min: int | None = None) -> None:
    """Shared logic for every 'must be a positive integer' config value
    (poll interval, HTTP timeout, retry interval) — kept in one place so
    each of the three doesn't duplicate its own parsing/error text."""
    raw = config.get(section, key, fallback=None)
    if raw is None or not raw.strip():
        errors.append(f"[{section}] {key} is missing or empty. {label} must be a whole number of seconds greater than 0.")
        return

    try:
        value = int(raw.strip())
    except ValueError:
        errors.append(f"[{section}] {key} = '{raw.strip()}' is not a valid integer. {label} must be a whole number of seconds.")
        return

    if value <= 0:
        errors.append(f"[{section}] {key} = {value} is invalid. {label} must be greater than 0.")
        return

    if recommended_min is not None and value < recommended_min:
        logger.warning("[%s] %s = %ds is below the recommended minimum of %ds for %s — "
                        "this may cause excessive load on the backend.",
                        section, key, value, recommended_min, label.lower())

    # Normalize (strips any stray whitespace) back into the live config object
    # so every existing config.get(...) call downstream sees the clean value.
    config.set(section, key, str(value))


def validate_config(config: configparser.ConfigParser, logger: logging.Logger) -> None:
    """Validates every value in config.ini, collecting ALL problems found
    (rather than stopping at the first) so a person fixing their config
    sees every issue in one pass instead of a slow fix-restart-fix loop.

    Also normalizes values in place (strips accidental whitespace,
    uppercases log_level) — SecureSocAgent.__init__ and setup_logging()
    are completely unchanged; they just transparently receive already-clean
    values through the same config.get(...) calls they always used.

    Raises ConfigValidationError with every problem listed if anything is
    invalid. Raises nothing if the config is valid.
    """
    errors: list[str] = []

    # --- 1. Server URL ---
    url = config.get("server", "url", fallback="").strip()
    if not url:
        errors.append("[server] url is empty. Set it to your backend's base URL, e.g. http://localhost:8080/api")
    elif not (url.startswith("http://") or url.startswith("https://")):
        errors.append(f"[server] url = '{url}' is invalid — it must start with http:// or https://")
    elif not urlparse(url).netloc:
        errors.append(f"[server] url = '{url}' is not a valid URL (missing host after the scheme).")
    else:
        config.set("server", "url", url)

    # --- 2. Registration Secret ---
    secret = config.get("server", "registration_secret", fallback="").strip()
    if not secret:
        errors.append("[server] registration_secret is empty. Ask your admin for the shared AGENT_REGISTRATION_SECRET value.")
    elif secret.lower() in _PLACEHOLDER_SECRETS or secret.upper().startswith("CHANGE_THIS"):
        errors.append(f"[server] registration_secret is still set to a placeholder value ('{secret}'). "
                       f"Replace it with the real secret from your admin.")
    else:
        if len(secret) < _MIN_SECRET_LENGTH:
            logger.warning("[server] registration_secret is only %d characters — recommend at least %d "
                            "for a strong shared secret.", len(secret), _MIN_SECRET_LENGTH)
        config.set("server", "registration_secret", secret)

    # --- 3. Poll (heartbeat) Interval ---
    _validate_positive_int(config, "agent", "heartbeat_interval_seconds", "Poll interval",
                            errors, logger, recommended_min=_RECOMMENDED_MIN_POLL_SECONDS)

    # --- 4. HTTP Timeout ---
    _validate_positive_int(config, "agent", "http_timeout_seconds", "HTTP timeout", errors, logger)

    # --- 5. Retry Interval ---
    _validate_positive_int(config, "agent", "retry_interval_seconds", "Retry interval", errors, logger)

    # --- 6. Log Level ---
    raw_level = config.get("agent", "log_level", fallback="INFO").strip().upper()
    if raw_level not in _VALID_LOG_LEVELS:
        errors.append(f"[agent] log_level = '{raw_level}' is invalid. Must be one of: {', '.join(sorted(_VALID_LOG_LEVELS))}")
    else:
        config.set("agent", "log_level", raw_level)

    # --- 7. Agent Name / Endpoint Name (optional — only validated if the key is present) ---
    if config.has_option("agent", "name"):
        name = config.get("agent", "name").strip()
        if not name:
            errors.append("[agent] name is present but empty. Either remove the key or set a non-empty value.")
        else:
            config.set("agent", "name", name)

    # --- 8. Offline Queue Settings (all optional — sensible defaults apply if absent,
    #        so existing config.ini files from before this feature keep working unchanged) ---
    for key, label in (
        ("offline_queue_max_size", "Offline queue max size"),
        ("offline_queue_max_db_size_mb", "Offline queue max DB size (MB)"),
        ("offline_queue_max_retry_count", "Offline queue max retry count"),
        ("offline_replay_interval_seconds", "Offline replay interval"),
        ("offline_replay_batch_size", "Offline replay batch size"),
        ("maintenance_interval_hours", "Maintenance interval (hours)"),
        ("vacuum_threshold", "VACUUM threshold"),
        ("queue_warning_threshold", "Queue warning threshold"),
        ("backoff_max_minutes", "Replay backoff max (minutes)"),
    ):
        if config.has_option("agent", key):
            _validate_positive_int(config, "agent", key, label, errors, logger)

    if config.has_option("agent", "offline_queue_on_full"):
        on_full = config.get("agent", "offline_queue_on_full").strip().lower()
        if on_full not in ("discard_oldest", "reject_new"):
            errors.append(f"[agent] offline_queue_on_full = '{on_full}' is invalid. "
                           f"Must be one of: discard_oldest, reject_new")
        else:
            config.set("agent", "offline_queue_on_full", on_full)

    if errors:
        raise ConfigValidationError("\n".join(f"  - {e}" for e in errors))


class AgentState:
    """Persists endpoint_id + agent_token locally so we don't re-register
    (and rotate the token) on every restart.

    On-disk format is an encrypted envelope:
        {"encryptionVersion": 1, "method": "dpapi"|"fernet", "ciphertext": "<base64>"}
    where the decrypted ciphertext is the exact same
    {"endpointId": ..., "agentToken": ...} JSON this file always held — so
    every existing validation rule below is unchanged and applies equally
    whether that JSON came from decryption or (during one-time migration)
    directly from a legacy plaintext file. See state_crypto.py for the
    encryption backends themselves.
    """

    def __init__(self, path: Path, logger: logging.Logger):
        self.path = path
        self.log = logger
        self.endpoint_id: str | None = None
        self.agent_token: str | None = None
        self._crypto = state_crypto.StateCrypto(path)
        self._load()

    def _quarantine(self, tag: str):
        """Renames the current state file out of the way (timestamped) so
        it's preserved for forensics, without blocking automatic
        re-registration. Shared by the corrupted-JSON and
        undecryptable-ciphertext recovery paths."""
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        quarantined_path = self.path.with_name(f"{self.path.stem}.{tag}.{timestamp}{self.path.suffix}")
        try:
            self.path.replace(quarantined_path)
        except OSError:
            self.log.warning("Could not preserve %s state file; continuing with re-registration anyway.", tag)

    def _decrypt_envelope(self, envelope: dict) -> dict | None:
        """Returns the decrypted {"endpointId": ..., "agentToken": ...} dict,
        or None if decryption failed (already logged + quarantined by this
        method before returning)."""
        method = envelope.get("method")
        ciphertext_b64 = envelope.get("ciphertext")
        if not isinstance(method, str) or not isinstance(ciphertext_b64, str):
            self.log.warning("Encrypted state file is malformed. Re-registering.")
            self._quarantine("corrupted")
            return None

        try:
            ciphertext = base64.b64decode(ciphertext_b64)
            plaintext_bytes = self._crypto.decrypt(ciphertext, method)
            return json.loads(plaintext_bytes.decode("utf-8"))
        except state_crypto.DecryptionError as exc:
            # exc's message is guaranteed to never include ciphertext/
            # plaintext/token content — safe to log directly.
            self.log.error("Could not decrypt state file: %s", exc)
            self._quarantine("undecryptable")
            return None
        except (json.JSONDecodeError, UnicodeDecodeError, ValueError):
            self.log.error("Decrypted state file content is invalid. Re-registering.")
            self._quarantine("undecryptable")
            return None
        except Exception:
            # Catch-all so a decryption backend surprise never becomes an
            # unhandled traceback. exc_info deliberately omitted here (unlike
            # other error logs in this class) as an extra precaution against
            # a crypto backend ever including buffer content in its own
            # exception representation.
            self.log.error("Unexpected error decrypting state file. Re-registering.")
            self._quarantine("undecryptable")
            return None

    def _load(self):
        if not self.path.exists():
            self.log.info("No existing state found. Registering endpoint.")
            return

        try:
            raw = self.path.read_text()
        except OSError:
            self.log.error("Unable to read state file.", exc_info=True)
            sys.exit(1)

        try:
            envelope = json.loads(raw)
        except json.JSONDecodeError:
            self.log.warning("State file is corrupted. Recovering automatically.")
            self._quarantine("corrupted")
            return

        is_legacy_plaintext = isinstance(envelope, dict) and "ciphertext" not in envelope and "endpointId" in envelope

        if is_legacy_plaintext:
            self.log.info("Plaintext state file detected — migrating to encrypted storage.")
            data = envelope
        elif isinstance(envelope, dict) and "ciphertext" in envelope:
            data = self._decrypt_envelope(envelope)
            if data is None:
                return  # already logged + quarantined above
        else:
            self.log.warning("State file has an unrecognized format. Re-registering.")
            self._quarantine("corrupted")
            return

        # Required fields: endpointId (non-empty string identifier) and
        # agentToken (non-empty string). Never log the actual values —
        # only which field, if any, failed validation.
        endpoint_id = data.get("endpointId")
        agent_token = data.get("agentToken")

        if not isinstance(endpoint_id, str) or not endpoint_id:
            self.log.warning("State file field 'endpointId' is missing or invalid. Re-registering.")
            return
        if not isinstance(agent_token, str) or not agent_token:
            self.log.warning("State file field 'agentToken' is missing or invalid. Re-registering.")
            return

        self.endpoint_id = endpoint_id
        self.agent_token = agent_token

        # Transparent one-time migration: a legacy plaintext file that just
        # validated successfully is immediately rewritten encrypted, so
        # plaintext never persists on disk past the first startup after
        # this upgrade. No user action, no separate migration step.
        if is_legacy_plaintext:
            self.save(endpoint_id, agent_token)

    def save(self, endpoint_id: str, agent_token: str):
        self.endpoint_id = endpoint_id
        self.agent_token = agent_token

        # Plaintext bytes exist only for the duration of this call — encrypted
        # immediately below and not retained anywhere after this method returns.
        # (self.endpoint_id / self.agent_token remain in memory for the process
        # lifetime, as they always did — every heartbeat/monitoring call needs
        # them; that is unchanged, pre-existing behaviour, not new exposure.)
        plaintext = json.dumps({
            "endpointId": endpoint_id,
            "agentToken": agent_token,
        }).encode("utf-8")

        try:
            method, ciphertext = self._crypto.encrypt(plaintext)
            envelope = {
                "encryptionVersion": 1,
                "method": method,
                "ciphertext": base64.b64encode(ciphertext).decode("ascii"),
            }
            self.path.write_text(json.dumps(envelope, indent=2))
        except OSError:
            self.log.error(
                "Unable to write state file %s — continuing with in-memory credentials for "
                "this run, but registration will be repeated on the next restart until this "
                "is fixed (check disk space / file permissions).",
                self.path, exc_info=True,
            )
        except Exception:
            # Encryption backend failure — never fall back to writing
            # plaintext; just keep running on in-memory credentials.
            self.log.error(
                "Unable to encrypt state for %s — continuing with in-memory credentials for "
                "this run without persisting to disk.", self.path,
            )

    def is_registered(self) -> bool:
        return bool(self.endpoint_id and self.agent_token)


class SecureSocAgent:

    def __init__(self, config: configparser.ConfigParser, logger: logging.Logger):
        self.config = config
        self.log = logger

        self.server_url = config.get("server", "url").rstrip("/")
        self.registration_secret = config.get("server", "registration_secret")
        self.lab_id = config.get("server", "lab_id", fallback="").strip() or None

        self.heartbeat_interval = config.getint("agent", "heartbeat_interval_seconds", fallback=15)
        self.monitoring_interval = config.getint("agent", "monitoring_interval_seconds", fallback=60)
        self.retry_interval = config.getint("agent", "retry_interval_seconds", fallback=10)
        self.http_timeout = config.getint("agent", "http_timeout_seconds", fallback=10)
        self.agent_version = config.get("agent", "version", fallback="0.1.0-PHASE2")

        state_file = config.get("agent", "state_file", fallback="agent_state.json")
        self.state = AgentState(Path(__file__).parent / state_file, self.log)

        self.session = requests.Session()
        self.network_tracker = collector.NetworkUsageTracker()
        self._last_monitoring_cycle = 0.0
        self._last_known_user = None

        # --- Offline event queue (monitoring events only — see offline_queue.py docstring) ---
        queue_db_file = config.get("agent", "offline_queue_db_file", fallback="offline_queue.db")
        self.offline_queue = offline_queue.OfflineQueue(
            Path(__file__).parent / queue_db_file, self.log,
            max_queue_size=config.getint("agent", "offline_queue_max_size", fallback=5000),
            max_db_size_bytes=config.getint("agent", "offline_queue_max_db_size_mb", fallback=50) * 1024 * 1024,
            max_retry_count=config.getint("agent", "offline_queue_max_retry_count", fallback=10),
            on_full=config.get("agent", "offline_queue_on_full", fallback="discard_oldest").strip().lower(),
            wal_enabled=config.getboolean("agent", "offline_queue_wal_enabled", fallback=True),
            integrity_check_enabled=config.getboolean("agent", "queue_integrity_check", fallback=True),
            vacuum_threshold=config.getint("agent", "vacuum_threshold", fallback=500),
        )
        self.replay_interval = config.getint("agent", "offline_replay_interval_seconds", fallback=30)
        self.replay_batch_size = config.getint("agent", "offline_replay_batch_size", fallback=50)
        self.queue_warning_threshold = config.getint("agent", "queue_warning_threshold", fallback=1000)
        self.maintenance_interval_seconds = config.getint("agent", "maintenance_interval_hours", fallback=24) * 3600
        self._last_maintenance_at = time.monotonic()

        # Exponential replay backoff: base = replay_interval (so an
        # untouched config.ini gets 30s/60s/120s/... exactly matching the
        # shipped 30s default), doubling each consecutive connectivity
        # failure, capped at backoff_max_minutes. Only ever read/written
        # from the replay thread itself — see _replay_worker — so it needs
        # no lock.
        backoff_max_seconds = config.getint("agent", "backoff_max_minutes", fallback=30) * 60
        self._backoff_schedule = self._build_backoff_schedule(self.replay_interval, backoff_max_seconds)
        self._backoff_step = 0

        self._replay_stop_event = threading.Event()
        self._replay_thread = threading.Thread(target=self._replay_worker, name="offline-replay", daemon=True)


    # ---------------------------------------------------------------

    def register_with_retry(self):
        payload = collector.collect_registration_payload(self.agent_version, self.lab_id)
        url = f"{self.server_url}/agents/register"
        headers = {"X-Registration-Secret": self.registration_secret}

        while True:
            try:
                self.log.info("Registering with backend as hostname=%s mac=%s",
                               payload["hostname"], payload["macAddress"])
                response = self.session.post(url, json=payload, headers=headers, timeout=self.http_timeout)

                if response.status_code == 401:
                    self.log.error("Registration rejected (401): check registration_secret in config.ini")
                elif response.ok:
                    data = response.json()
                    self.state.save(data["endpointId"], data["agentToken"])
                    self.log.info("Registered successfully. endpoint_id=%s status=%s message=%s",
                                  data["endpointId"], data["status"], data.get("message"))
                    return
                else:
                    self.log.error("Registration failed: HTTP %s %s", response.status_code, response.text)

            except requests.exceptions.RequestException as ex:
                self.log.warning("Backend unreachable during registration: %s", ex)

            self.log.info("Retrying registration in %ss...", self.retry_interval)
            time.sleep(self.retry_interval)

    def send_heartbeat(self) -> bool:
        payload = collector.collect_heartbeat_payload()
        url = f"{self.server_url}/agents/heartbeat"
        headers = {"X-Agent-Token": self.state.agent_token}

        try:
            response = self.session.post(url, json=payload, headers=headers, timeout=self.http_timeout)

            if response.status_code == 401:
                self.log.warning("Heartbeat rejected (401) — token invalid/rotated. Re-registering.")
                self.state.endpoint_id = None
                self.state.agent_token = None
                return False

            if response.ok:
                data = response.json()
                self.log.info("Heartbeat OK — status=%s cpu=%s%% ram=%s%% disk=%s%%",
                              data.get("status"), payload["cpuUsagePct"],
                              payload["ramUsagePct"], payload["diskUsagePct"])
                return True

            self.log.error("Heartbeat failed: HTTP %s %s", response.status_code, response.text)
            return True  # transient server error — don't re-register, just retry next cycle

        except requests.exceptions.RequestException as ex:
            self.log.warning("Backend unreachable during heartbeat: %s", ex)
            return True  # network blip — keep the token, retry next cycle

    # ---------------------------------------------------------------
    # Phase 3 — Core Monitoring
    # ---------------------------------------------------------------

    def _post_monitoring(self, path: str, payload: dict) -> bool:
        """Shared helper for all /monitoring/** ingest calls. Returns True
        if the call should be considered handled (success, a queued-for-
        retry failure, or a non-retryable client error we just log and
        move on from); False only on auth failure, which the caller should
        treat like a heartbeat 401.

        On connection failure, timeout, or a 5xx response, the event is
        queued to the offline SQLite queue for later replay instead of
        being dropped — everything else about this method's behaviour and
        return contract is unchanged from before."""
        url = f"{self.server_url}/monitoring/{path}"
        headers = {"X-Agent-Token": self.state.agent_token}
        try:
            response = self.session.post(url, json=payload, headers=headers, timeout=self.http_timeout)
            if response.status_code == 401:
                self.log.warning("Monitoring call to %s rejected (401) — token invalid.", path)
                return False
            if response.status_code >= 500:
                self.log.error("Monitoring call to %s failed: HTTP %s %s — queuing for retry.",
                                path, response.status_code, response.text)
                self._enqueue_offline(path, payload)
                return True
            if not response.ok:
                self.log.error("Monitoring call to %s failed: HTTP %s %s", path, response.status_code, response.text)
            return True
        except requests.exceptions.RequestException as ex:
            self.log.warning("Backend unreachable during %s: %s — queuing for retry.", path, ex)
            self._enqueue_offline(path, payload)
            return True

    def _enqueue_offline(self, path: str, payload: dict):
        try:
            self.offline_queue.enqueue(path, payload, datetime.now(timezone.utc).isoformat())
        except Exception:
            # offline_queue's own methods already catch their internal
            # errors and return False rather than raising — this is an
            # extra safety net so a queue-layer bug can never take down
            # the monitoring call path that's calling us.
            self.log.error("Unexpected error queuing offline event for '%s'.", path, exc_info=True)

    # -----------------------------------------------------------------
    # Replay backoff
    # -----------------------------------------------------------------

    @staticmethod
    def _build_backoff_schedule(base_seconds: int, max_seconds: int) -> "list[int]":
        """Doubling exponential backoff starting at base_seconds, capped at
        max_seconds. With the shipped defaults (base=30s, max=30min) this
        produces 30s, 60s, 120s, 240s, 480s, 960s, then holds at 1800s
        (30min) for every step after — the same overall shape as the
        worked example of 30s/1m/2m/5m/.../30min cap, using clean doubling
        rather than an uneven 2.5x jump so the algorithm itself stays
        simple and predictable."""
        schedule = []
        value = max(base_seconds, 1)
        while value < max_seconds:
            schedule.append(value)
            value *= 2
        schedule.append(max_seconds)
        return schedule

    def _current_backoff_seconds(self) -> int:
        idx = min(self._backoff_step, len(self._backoff_schedule) - 1)
        return self._backoff_schedule[idx]

    def _reset_backoff(self):
        if self._backoff_step != 0:
            self.log.info("Replay backoff reset to base interval (%ds) after a successful replay.", self.replay_interval)
        self._backoff_step = 0

    def _advance_backoff(self):
        previous = self._current_backoff_seconds()
        self._backoff_step += 1
        new_interval = self._current_backoff_seconds()
        if new_interval != previous:
            self.log.warning("Replay backoff increased to %ds after a failed replay attempt.", new_interval)

    # -----------------------------------------------------------------
    # Health metrics (internal only for now — not sent to the backend;
    # shaped for a future dashboard/reporting endpoint to consume as-is)
    # -----------------------------------------------------------------

    def get_offline_queue_health(self) -> dict:
        health = self.offline_queue.get_stats()
        health["current_backoff_seconds"] = self._current_backoff_seconds()
        return health

    # -----------------------------------------------------------------
    # Replay
    # -----------------------------------------------------------------

    def _replay_worker(self):
        """Background thread: periodically drains the offline queue whenever
        the backend is reachable again. Uses its own requests.Session —
        never touches self.session, which the main thread's heartbeat/
        monitoring loop owns — and the offline_queue's own connection-per-
        call design means no shared sqlite3.Connection either. This is what
        lets replay (and periodic maintenance, piggybacked on this same
        thread rather than spinning up a third one) run without blocking
        or racing ongoing monitoring."""
        session = requests.Session()
        while not self._replay_stop_event.wait(self._current_backoff_seconds()):
            if self.state.is_registered():
                try:
                    any_success, hit_failure = self._replay_batch(session)
                    if any_success:
                        self._reset_backoff()
                    elif hit_failure:
                        self._advance_backoff()
                except Exception:
                    self.log.error("Unexpected error during offline queue replay — will retry next interval.", exc_info=True)

            stats = self.offline_queue.get_stats()
            if stats["queue_size"] > self.queue_warning_threshold:
                self.log.warning("Offline queue exceeds configured threshold (%d > %d records).",
                                  stats["queue_size"], self.queue_warning_threshold)

            if time.monotonic() - self._last_maintenance_at >= self.maintenance_interval_seconds:
                self.offline_queue.run_maintenance()
                self._last_maintenance_at = time.monotonic()

    def _replay_batch(self, session: requests.Session) -> "tuple[bool, bool]":
        """Drains the queue in successive replay_batch_size chunks within
        a single call — not just one batch — so a large backlog recovers
        in one pass instead of trickling out one batch per backoff
        interval (commit a batch, then immediately pull the next one).
        Stops immediately on the first connectivity-type failure,
        preserving FIFO order for the next attempt. Runs entirely on the
        replay thread, so this never delays the main loop's heartbeats or
        monitoring collection regardless of how large the backlog is.

        Returns (any_success, hit_failure) so the caller can drive the
        exponential backoff state machine."""
        any_success = False
        replay_started_logged = False

        while True:
            batch = self.offline_queue.peek_batch(self.replay_batch_size)
            if not batch:
                self.log.info("Replay completed." if replay_started_logged else "Queue empty.")
                return any_success, False

            if not replay_started_logged:
                self.log.info("Replay started.")
                replay_started_logged = True

            for record in batch:
                headers = {"X-Agent-Token": self.state.agent_token}  # always the CURRENT token, never a stored one
                url = f"{self.server_url}/monitoring/{record.path}"
                try:
                    response = session.post(url, json=record.payload, headers=headers, timeout=self.http_timeout)
                except requests.exceptions.RequestException as ex:
                    # Outcome genuinely unknown (we don't know if the backend
                    # received it before the connection dropped) — leave it
                    # queued and stop so ordering is preserved for the next
                    # attempt, rather than skipping ahead.
                    self.log.warning("Replay failure — backend still unreachable (%s).", ex)
                    return any_success, True

                if response.status_code == 401:
                    self.log.warning("Replay failure — agent token rejected. Will resume with a fresh token.")
                    return any_success, True

                if response.ok:
                    self.offline_queue.delete(record.id)
                    self.offline_queue.record_replay_success()
                    any_success = True
                    continue

                if response.status_code >= 500:
                    self.log.warning("Replay failure — queued '%s' event failed (HTTP %s). Stopping to preserve order.",
                                      record.path, response.status_code)
                    self.offline_queue.record_replay_failure()
                    new_count = self.offline_queue.increment_retry(record.id)
                    if new_count is not None and new_count >= self.offline_queue.max_retry_count:
                        record.retry_count = new_count
                        self.offline_queue.quarantine(
                            record, f"Exceeded max retry count ({self.offline_queue.max_retry_count}) "
                                    f"— last error HTTP {response.status_code}.")
                    return any_success, True

                # A non-5xx, non-401 rejection (e.g. 400) is a data problem,
                # not a connectivity problem — retrying it won't help, and
                # letting one bad record block the entire queue forever
                # would be worse than quarantining it and moving on.
                self.log.error("Queued '%s' event rejected by backend (HTTP %s) — quarantining, continuing replay.",
                                record.path, response.status_code)
                self.offline_queue.record_replay_failure()
                self.offline_queue.quarantine(record, f"HTTP {response.status_code} on replay: {response.text[:200]}")

            # Batch fully processed with no connectivity-type failure —
            # loop straight into the next batch (the "commit -> next 100"
            # flow) rather than waiting for the next backoff interval.

    def run_monitoring_cycle(self) -> bool:
        """Called every monitoring_interval_seconds — sends the lower-frequency
        signals (running apps, network/internet usage, VPN status, idle time).
        Login/logout are event-driven (see on_startup/on_shutdown) rather than
        polled here."""
        ok = True

        # Running applications snapshot
        try:
            apps = collector.get_running_applications()
        except Exception:
            self.log.error("Collector 'get_running_applications' failed — skipping running-apps data for this cycle.", exc_info=True)
            apps = None
        if apps:
            ok &= self._post_monitoring("running-apps", {"applications": apps})

        # Network + internet usage (delta since last sample)
        try:
            sample = self.network_tracker.sample()
        except Exception:
            self.log.error("Collector 'NetworkUsageTracker.sample' failed — skipping network/internet usage data for this cycle.", exc_info=True)
            sample = None
        if sample is not None:
            ok &= self._post_monitoring("network-usage", sample["network"])
            ok &= self._post_monitoring("internet-usage", sample["internet"])

        # VPN adapters
        try:
            vpn_adapters = collector.detect_vpn_adapters()
        except Exception:
            self.log.error("Collector 'detect_vpn_adapters' failed — skipping VPN data for this cycle.", exc_info=True)
            vpn_adapters = None
        if vpn_adapters is not None:
            if vpn_adapters:
                for adapter in vpn_adapters:
                    ok &= self._post_monitoring("vpn", {"adapterName": adapter, "active": True})
            else:
                ok &= self._post_monitoring("vpn", {"adapterName": None, "active": False})

        # Idle time (returns None on non-Windows — see collector.get_idle_seconds docstring)
        try:
            idle_seconds = collector.get_idle_seconds()
        except Exception:
            self.log.error("Collector 'get_idle_seconds' failed — skipping idle data for this cycle.", exc_info=True)
            idle_seconds = None
        if idle_seconds is not None:
            ok &= self._post_monitoring("idle", {"idleSeconds": idle_seconds})

        return ok

    def send_login_event(self):
        current_user = collector.get_current_os_user()
        self._last_known_user = current_user
        self._post_monitoring("login", {
            "osUsername": current_user,
            "sessionId": None,
            "loginTime": datetime.now(timezone.utc).isoformat(),
        })

    def send_logout_event(self):
        self._post_monitoring("logout", {
            "osUsername": self._last_known_user or collector.get_current_os_user(),
            "sessionId": None,
            "logoutTime": datetime.now(timezone.utc).isoformat(),
        })

    # ---------------------------------------------------------------

    def run_forever(self):
        self.log.info("SecureSOC agent starting. Server: %s", self.server_url)

        if not self.state.is_registered():
            self.register_with_retry()

        self._replay_thread.start()

        self.send_login_event()
        self._last_monitoring_cycle = time.monotonic()

        try:
            while True:
                still_valid = self.send_heartbeat()
                if not still_valid:
                    self.register_with_retry()

                if time.monotonic() - self._last_monitoring_cycle >= self.monitoring_interval:
                    self.run_monitoring_cycle()
                    self._last_monitoring_cycle = time.monotonic()

                time.sleep(self.heartbeat_interval)
        finally:
            self._replay_stop_event.set()
            self._replay_thread.join(timeout=5)
            self.send_logout_event()


def main():
    config = load_config()

    # Validation runs before the real (rotating-file) logger exists — a
    # minimal stdout-only bootstrap logger is enough for this step, whose
    # entire purpose is to fail fast, before any log file is opened, before
    # any HTTP session is created, and before registration/heartbeat/
    # monitoring ever starts.
    bootstrap_logger = logging.getLogger("securesoc-agent.startup")
    if not bootstrap_logger.handlers:
        bootstrap_logger.addHandler(logging.StreamHandler(sys.stdout))
        bootstrap_logger.setLevel(logging.INFO)
        bootstrap_logger.propagate = False

    try:
        validate_config(config, bootstrap_logger)
    except ConfigValidationError as exc:
        bootstrap_logger.error("Invalid configuration in %s:\n%s", CONFIG_PATH, exc)
        sys.exit(1)

    logger = setup_logging(config.get("agent", "log_level", fallback="INFO"))
    agent = SecureSocAgent(config, logger)

    try:
        agent.run_forever()
    except KeyboardInterrupt:
        logger.info("Agent stopped by user.")


if __name__ == "__main__":
    main()
