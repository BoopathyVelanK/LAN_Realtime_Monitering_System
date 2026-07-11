"""
offline_queue.py — Persistent SQLite queue for monitoring events that
couldn't be delivered (backend unreachable, timed out, or returned a
temporary 5xx).

Scope note (a deliberate, documented design choice — see agent.py's
integration comments): this buffers /monitoring/** events only (login,
logout, idle, running-apps, usb, vpn, network-usage, internet-usage).
Heartbeats and registration are intentionally NOT queued here:
  - A heartbeat is a point-in-time liveness signal ("I am online right
    now"). Replaying a stale one later would misrepresent history rather
    than preserve it, and the heartbeat loop already retries implicitly
    every heartbeat_interval_seconds.
  - Registration has its own existing blocking retry-until-success loop
    (register_with_retry) and happens before any token exists — there is
    nothing meaningful to "replay" for it.

Design choices worth calling out:
  - The agent's auth token (X-Agent-Token) is deliberately NOT persisted
    in this queue, even though it's the only HTTP header these calls use.
    It's a bearer secret, and if the token were to rotate (e.g. after a
    401 forces re-registration) between when an event was queued and when
    it's replayed, a stored old token would just fail replay anyway.
    Instead, replay always builds the header fresh from whatever token the
    agent holds *at replay time*. Only the relative monitoring path (e.g.
    "usb"), the JSON payload, and timestamps are stored.
  - Every write opens and closes its own short-lived sqlite3 connection
    (no connection is held open across the process) — this is what makes
    "automatic recovery after restart" free: whatever's on disk when the
    agent restarts *is* the queue's state, nothing else to reconstruct.
  - WAL mode is used so replay (a background thread) and the main thread's
    occasional enqueue-on-failure never block each other for reads; a
    Python-level lock still serializes writers explicitly, as a second
    layer on top of SQLite's own busy_timeout-based locking, rather than
    relying on the timeout alone.
"""

import json
import logging
import os
import sqlite3
import threading
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional


@dataclass
class QueuedRecord:
    id: int
    path: str
    payload: dict
    event_timestamp: str
    queued_at: str
    retry_count: int


class OfflineQueue:
    """SQLite-backed FIFO queue for monitoring events awaiting delivery."""

    def __init__(self, db_path: Path, logger: logging.Logger, *,
                 max_queue_size: int, max_db_size_bytes: int,
                 max_retry_count: int, on_full: str, wal_enabled: bool,
                 integrity_check_enabled: bool = True, vacuum_threshold: int = 500):
        self.db_path = db_path
        self.log = logger
        self.max_queue_size = max_queue_size
        self.max_db_size_bytes = max_db_size_bytes
        self.max_retry_count = max_retry_count
        self.on_full = on_full if on_full in ("discard_oldest", "reject_new") else "discard_oldest"
        self.wal_enabled = wal_enabled
        self.integrity_check_enabled = integrity_check_enabled
        self.vacuum_threshold = vacuum_threshold
        self._write_lock = threading.Lock()
        self._available = True
        self._init_db()

    # -----------------------------------------------------------------
    # Connection / schema
    # -----------------------------------------------------------------

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path), timeout=10, isolation_level=None)
        conn.execute("PRAGMA busy_timeout = 10000")
        if self.wal_enabled:
            conn.execute("PRAGMA journal_mode = WAL")
        return conn

    @contextmanager
    def _transaction(self):
        """Crash-safe write: BEGIN IMMEDIATE acquires the write lock up
        front (rather than on first write statement), so a concurrent
        writer blocks/fails fast instead of the two interleaving. Rolls
        back on any sqlite3.Error and always closes the connection."""
        conn = self._connect()
        try:
            conn.execute("BEGIN IMMEDIATE")
            yield conn
            conn.execute("COMMIT")
        except sqlite3.Error:
            conn.execute("ROLLBACK")
            raise
        finally:
            conn.close()

    def _init_db(self):
        try:
            is_new = not self.db_path.exists()

            if not is_new and self.integrity_check_enabled:
                if not self._check_integrity():
                    self._quarantine_corrupted_db()
                    is_new = True  # the path is now clear for a fresh file

            conn = self._connect()
            try:
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS offline_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        path TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        event_timestamp TEXT NOT NULL,
                        queued_at TEXT NOT NULL,
                        retry_count INTEGER NOT NULL DEFAULT 0
                    )
                """)
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS offline_queue_quarantine (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        original_id INTEGER,
                        path TEXT,
                        payload TEXT,
                        event_timestamp TEXT,
                        queued_at TEXT,
                        retry_count INTEGER,
                        reason TEXT NOT NULL,
                        quarantined_at TEXT NOT NULL
                    )
                """)
                conn.execute("""
                    CREATE TABLE IF NOT EXISTS offline_queue_meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                """)
            finally:
                conn.close()
            if is_new:
                try:
                    os.chmod(self.db_path, 0o600)
                except OSError:
                    pass
            self._available = True
        except sqlite3.Error:
            self.log.error(
                "Could not initialize offline queue database at %s — offline "
                "buffering is disabled for this run; monitoring will continue, "
                "but events will be dropped instead of queued while the backend "
                "is unreachable until this is fixed.",
                self.db_path, exc_info=True,
            )
            self._available = False

    def _check_integrity(self) -> bool:
        """PRAGMA integrity_check — run once at startup (only when the
        database file already exists) so a corrupted DB from an unclean
        shutdown or disk fault is caught before anything tries to read/
        write it, rather than surfacing as confusing per-operation errors
        later. Any failure to even open the file for checking is treated
        the same as a failed check (corrupted)."""
        try:
            conn = self._connect()
            try:
                result = conn.execute("PRAGMA integrity_check").fetchone()
                ok = bool(result) and str(result[0]).strip().lower() == "ok"
                if not ok:
                    self.log.error(
                        "Database corruption detected in offline queue: %s",
                        result[0] if result else "integrity_check returned no result",
                    )
                return ok
            finally:
                conn.close()
        except sqlite3.Error:
            self.log.error("Offline queue database could not be opened for integrity check — treating as corrupted.", exc_info=True)
            return False

    def _quarantine_corrupted_db(self):
        """Preserves the corrupted file (and its WAL/SHM siblings, if any)
        under a timestamped name rather than deleting it, then lets the
        caller create a fresh database in its place. Buffered events in
        the corrupted file are not recoverable, but monitoring — and
        buffering of *new* events — continues uninterrupted."""
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        quarantined = self.db_path.with_name(f"{self.db_path.stem}.corrupted.{timestamp}{self.db_path.suffix}")
        self.log.error(
            "Preserving corrupted offline queue database as %s and starting a fresh one. "
            "Monitoring continues; events previously buffered in the corrupted file could not be recovered.",
            quarantined.name,
        )
        for suffix in ("", "-wal", "-shm"):
            src = Path(str(self.db_path) + suffix)
            if not src.exists():
                continue
            dst = Path(str(quarantined) + suffix) if suffix else quarantined
            try:
                src.replace(dst)
            except OSError:
                self.log.error("Could not move aside corrupted file %s — will attempt to continue anyway.", src, exc_info=True)

    def _current_db_size_bytes(self) -> int:
        total = 0
        for suffix in ("", "-wal", "-shm"):
            p = Path(str(self.db_path) + suffix)
            if p.exists():
                total += p.stat().st_size
        return total

    # -----------------------------------------------------------------
    # Enqueue
    # -----------------------------------------------------------------

    def enqueue(self, path: str, payload: dict, event_timestamp: str) -> bool:
        """Adds one event to the queue. Never raises — on any failure
        (SQLite unavailable, disk full, unexpected error) logs and returns
        False so the caller (agent.py) can carry on without crashing."""
        if not self._available:
            return False

        with self._write_lock:
            try:
                payload_json = json.dumps(payload)
                with self._transaction() as conn:
                    count = conn.execute("SELECT COUNT(*) FROM offline_queue").fetchone()[0]
                    if count >= self.max_queue_size:
                        self.log.warning(
                            "Offline queue is full (%d/%d records) — applying overflow policy '%s'.",
                            count, self.max_queue_size, self.on_full,
                        )
                        if self.on_full == "reject_new":
                            return False
                        conn.execute("DELETE FROM offline_queue WHERE id = (SELECT MIN(id) FROM offline_queue)")

                    if self._current_db_size_bytes() >= self.max_db_size_bytes:
                        self.log.warning(
                            "Offline queue database at/over its configured max size (%d bytes) — "
                            "dropping the oldest queued record to make room.",
                            self.max_db_size_bytes,
                        )
                        conn.execute("DELETE FROM offline_queue WHERE id = (SELECT MIN(id) FROM offline_queue)")

                    conn.execute(
                        "INSERT INTO offline_queue (path, payload, event_timestamp, queued_at, retry_count) "
                        "VALUES (?, ?, ?, ?, 0)",
                        (path, payload_json, event_timestamp, datetime.now(timezone.utc).isoformat()),
                    )
                return True
            except Exception:
                self.log.error("Offline queue write failed for '%s' — event dropped for this attempt.", path, exc_info=True)
                return False

    # -----------------------------------------------------------------
    # Replay support
    # -----------------------------------------------------------------

    def peek_batch(self, limit: int) -> "list[QueuedRecord]":
        """Returns up to `limit` oldest-first records without removing
        them (removal only happens on confirmed successful upload — see
        agent.py's replay loop). A record whose stored payload JSON can't
        be parsed is quarantined immediately and skipped, so one corrupted
        row never blocks the rest of the batch."""
        if not self._available:
            return []

        try:
            conn = self._connect()
            try:
                rows = conn.execute(
                    "SELECT id, path, payload, event_timestamp, queued_at, retry_count "
                    "FROM offline_queue ORDER BY id ASC LIMIT ?",
                    (limit,),
                ).fetchall()
            finally:
                conn.close()
        except Exception:
            self.log.error("Offline queue read failed during replay.", exc_info=True)
            return []

        records = []
        for record_id, path, payload_json, event_timestamp, queued_at, retry_count in rows:
            try:
                payload = json.loads(payload_json)
            except json.JSONDecodeError:
                self.log.error("Queued record %s has corrupted payload JSON — quarantining.", record_id)
                self._quarantine_raw(record_id, path, payload_json, event_timestamp, queued_at, retry_count,
                                      reason="Stored payload JSON could not be parsed.")
                continue
            records.append(QueuedRecord(record_id, path, payload, event_timestamp, queued_at, retry_count))
        return records

    def delete(self, record_id: int):
        """Removes a record after its replay upload was confirmed
        successful (2xx response received)."""
        with self._write_lock:
            try:
                with self._transaction() as conn:
                    conn.execute("DELETE FROM offline_queue WHERE id = ?", (record_id,))
                    self._bump_deleted_counter(conn)
            except Exception:
                self.log.error(
                    "Failed to remove replayed record %s from the offline queue — it may be re-sent next replay.",
                    record_id, exc_info=True,
                )

    def increment_retry(self, record_id: int) -> Optional[int]:
        """Increments retry_count and returns the new value, or None if
        the update failed (queue unavailable etc.) — callers should treat
        None conservatively (i.e. not assume the retry limit was hit)."""
        with self._write_lock:
            try:
                with self._transaction() as conn:
                    conn.execute("UPDATE offline_queue SET retry_count = retry_count + 1 WHERE id = ?", (record_id,))
                    row = conn.execute("SELECT retry_count FROM offline_queue WHERE id = ?", (record_id,)).fetchone()
                return row[0] if row else None
            except Exception:
                self.log.error("Failed to update retry count for queued record %s.", record_id, exc_info=True)
                return None

    def quarantine(self, record: QueuedRecord, reason: str):
        """Moves a record out of the live queue into the quarantine table
        (rather than deleting it outright), preserving it for later
        inspection, and removes it from offline_queue so replay can move
        on to the next record instead of retrying it forever."""
        self._quarantine_raw(record.id, record.path, json.dumps(record.payload),
                              record.event_timestamp, record.queued_at, record.retry_count, reason)

    def _quarantine_raw(self, record_id, path, payload_json, event_timestamp, queued_at, retry_count, reason):
        with self._write_lock:
            try:
                with self._transaction() as conn:
                    conn.execute(
                        "INSERT INTO offline_queue_quarantine "
                        "(original_id, path, payload, event_timestamp, queued_at, retry_count, reason, quarantined_at) "
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        (record_id, path, payload_json, event_timestamp, queued_at, retry_count,
                         reason, datetime.now(timezone.utc).isoformat()),
                    )
                    conn.execute("DELETE FROM offline_queue WHERE id = ?", (record_id,))
                    self._bump_deleted_counter(conn)
            except Exception:
                self.log.error("Failed to quarantine queued record %s.", record_id, exc_info=True)

    def _bump_deleted_counter(self, conn: sqlite3.Connection):
        """Increments the running count of rows deleted since the last
        VACUUM. Called from within delete()/_quarantine_raw()'s own
        transaction (same connection, same commit) — not a separate write,
        so this adds no extra commits."""
        row = conn.execute("SELECT value FROM offline_queue_meta WHERE key = 'deleted_since_vacuum'").fetchone()
        new_value = (int(row[0]) if row else 0) + 1
        conn.execute(
            "INSERT INTO offline_queue_meta (key, value) VALUES ('deleted_since_vacuum', ?) "
            "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (str(new_value),),
        )

    # -----------------------------------------------------------------
    # Metrics
    # -----------------------------------------------------------------

    def record_replay_success(self):
        self._bump_meta_counter("total_replayed")
        self._set_meta("last_successful_replay_at", datetime.now(timezone.utc).isoformat())

    def record_replay_failure(self):
        self._bump_meta_counter("total_failed_replay_attempts")

    def _bump_meta_counter(self, key: str):
        with self._write_lock:
            try:
                with self._transaction() as conn:
                    row = conn.execute("SELECT value FROM offline_queue_meta WHERE key = ?", (key,)).fetchone()
                    new_value = (int(row[0]) if row else 0) + 1
                    conn.execute(
                        "INSERT INTO offline_queue_meta (key, value) VALUES (?, ?) "
                        "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                        (key, str(new_value)),
                    )
            except Exception:
                self.log.error("Failed to update offline queue metric '%s'.", key, exc_info=True)

    def _set_meta(self, key: str, value: str):
        with self._write_lock:
            try:
                with self._transaction() as conn:
                    conn.execute(
                        "INSERT INTO offline_queue_meta (key, value) VALUES (?, ?) "
                        "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                        (key, value),
                    )
            except Exception:
                self.log.error("Failed to update offline queue metadata '%s'.", key, exc_info=True)

    def get_stats(self) -> dict:
        stats = {
            "queue_size": 0,
            "oldest_queued_age_seconds": None,
            "last_successful_replay_at": None,
            "total_replayed": 0,
            "total_failed_replay_attempts": 0,
        }
        if not self._available:
            return stats
        try:
            conn = self._connect()
            try:
                row = conn.execute("SELECT COUNT(*), MIN(queued_at) FROM offline_queue").fetchone()
                stats["queue_size"] = row[0] or 0
                if row[1]:
                    oldest = datetime.fromisoformat(row[1])
                    stats["oldest_queued_age_seconds"] = (datetime.now(timezone.utc) - oldest).total_seconds()
                for key in ("last_successful_replay_at", "total_replayed", "total_failed_replay_attempts"):
                    meta_row = conn.execute("SELECT value FROM offline_queue_meta WHERE key = ?", (key,)).fetchone()
                    if meta_row:
                        stats[key] = meta_row[0]
            finally:
                conn.close()
        except Exception:
            self.log.error("Failed to read offline queue stats.", exc_info=True)
        return stats

    # -----------------------------------------------------------------
    # Maintenance
    # -----------------------------------------------------------------

    def run_maintenance(self):
        """Called periodically (see agent.py's replay thread) — never on
        every delete. PRAGMA optimize is cheap and safe to run every time;
        VACUUM (which rewrites the whole file and is comparatively
        expensive) only runs once enough rows have been deleted since the
        last VACUUM to justify reclaiming the space, tracked via the
        existing generic meta table rather than a new column."""
        if not self._available:
            return
        with self._write_lock:
            try:
                conn = self._connect()
                try:
                    conn.execute("PRAGMA optimize")
                    row = conn.execute(
                        "SELECT value FROM offline_queue_meta WHERE key = 'deleted_since_vacuum'"
                    ).fetchone()
                    deleted_since_vacuum = int(row[0]) if row else 0

                    if deleted_since_vacuum >= self.vacuum_threshold:
                        self.log.warning(
                            "Database maintenance running: VACUUM (%d rows deleted since last VACUUM, threshold %d).",
                            deleted_since_vacuum, self.vacuum_threshold,
                        )
                        conn.execute("VACUUM")
                        conn.execute(
                            "INSERT INTO offline_queue_meta (key, value) VALUES ('deleted_since_vacuum', '0') "
                            "ON CONFLICT(key) DO UPDATE SET value = '0'"
                        )
                    else:
                        self.log.info(
                            "Database maintenance running: PRAGMA optimize (%d/%d rows deleted since last VACUUM).",
                            deleted_since_vacuum, self.vacuum_threshold,
                        )
                finally:
                    conn.close()
            except Exception:
                self.log.error("Offline queue maintenance failed — will retry at the next scheduled interval.", exc_info=True)
