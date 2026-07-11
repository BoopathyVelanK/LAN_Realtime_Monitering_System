"""
collector.py — gathers the read-only system facts the backend needs.

This module contains ZERO business logic and makes ZERO security decisions
(per the SRS: "The endpoint agent acts purely as a telemetry collector").
It only answers: "what does this machine look like, right now?"
"""

import getpass
import platform
import socket
import time
import uuid

import psutil


def get_hostname() -> str:
    return socket.gethostname()


def get_mac_address() -> str:
    """Returns the MAC address of the primary network interface as
    'AA:BB:CC:DD:EE:FF'. Uses uuid.getnode(), which is portable but can
    fall back to a randomly-generated value on some virtual machines —
    good enough to uniquely key a device for Phase 2."""
    mac_num = uuid.getnode()
    mac_hex = ":".join(f"{(mac_num >> ele) & 0xff:02x}" for ele in range(40, -8, -8))
    return mac_hex.upper()


def get_ip_address() -> str:
    """Best-effort LAN IP — opens a UDP socket to a public IP without
    actually sending any traffic, just to see which local interface the
    OS would route through."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return socket.gethostbyname(socket.gethostname())
    finally:
        s.close()


def get_os_name() -> str:
    return platform.system()  # 'Windows', 'Linux', 'Darwin'


def get_os_version() -> str:
    return platform.version()


def get_cpu_info() -> str:
    info = platform.processor()
    return info if info else platform.machine()


def get_ram_mb() -> int:
    return round(psutil.virtual_memory().total / (1024 * 1024))


def get_disk_gb() -> int:
    root = "C:\\" if platform.system() == "Windows" else "/"
    return round(psutil.disk_usage(root).total / (1024 ** 3))


def collect_registration_payload(agent_version: str, lab_id: str | None) -> dict:
    """Everything the backend needs for POST /agents/register."""
    payload = {
        "hostname": get_hostname(),
        "macAddress": get_mac_address(),
        "ipAddress": get_ip_address(),
        "osName": get_os_name(),
        "osVersion": get_os_version(),
        "cpuInfo": get_cpu_info(),
        "ramMb": get_ram_mb(),
        "diskGb": get_disk_gb(),
        "agentVersion": agent_version,
    }
    if lab_id:
        payload["labId"] = lab_id
    return payload


def collect_heartbeat_payload() -> dict:
    """Live resource usage for POST /agents/heartbeat. Uses a short blocking
    interval so the first cpu_percent() reading isn't the meaningless '0.0'
    psutil returns on an uncalibrated first call."""
    cpu_pct = psutil.cpu_percent(interval=1)
    ram_pct = psutil.virtual_memory().percent
    root = "C:\\" if platform.system() == "Windows" else "/"
    disk_pct = psutil.disk_usage(root).percent

    return {
        "cpuUsagePct": round(cpu_pct, 2),
        "ramUsagePct": round(ram_pct, 2),
        "diskUsagePct": round(disk_pct, 2),
        "ipAddress": get_ip_address(),
    }


# =====================================================================
# Phase 3 — Core Monitoring collectors
# =====================================================================

def get_current_os_user() -> str:
    """OS-level logged-in username — NOT the SecureSOC portal user.
    Used for login/logout + running-app attribution."""
    try:
        return getpass.getuser()
    except Exception:
        return "unknown"


def get_running_applications(limit: int = 50) -> list[dict]:
    """Snapshot of currently running processes.

    NOTE — window_title: getting the actual foreground window title needs
    OS-specific APIs (pywin32's GetForegroundWindow on Windows). This is a
    cross-platform Phase 3 build, so window_title is left None here; wiring
    it up is a small, well-contained addition once this runs on a real
    Windows lab machine with pywin32 installed.
    """
    apps = []
    for proc in psutil.process_iter(["pid", "name"]):
        try:
            apps.append({
                "processName": proc.info.get("name"),
                "windowTitle": None,
                "pid": proc.info.get("pid"),
            })
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            continue
        if len(apps) >= limit:
            break
    return apps


def get_idle_seconds() -> int | None:
    """Seconds since last keyboard/mouse input.

    NOTE: There is no cross-platform way to read this with psutil alone.
    On Windows, this would call GetLastInputInfo via pywin32/ctypes. Left
    as None here (agent skips sending an idle event when None) so the
    Phase 3 build stays testable outside Windows; swap in the real
    Windows call before deploying to lab machines.
    """
    return None


def detect_vpn_adapters() -> list[str]:
    """Best-effort VPN detection: look for network interface names commonly
    used by VPN clients (tun/tap/ppp/wireguard/openvpn/nord/vpn). This is a
    heuristic, not a security control — the real detection logic (and the
    decision of what counts as a policy violation) lives in the Java
    detection engine (Phase 4), not here."""
    vpn_keywords = ("tun", "tap", "ppp", "wg", "vpn", "nord", "openvpn")
    matches = []
    for name in psutil.net_if_addrs().keys():
        lowered = name.lower()
        if any(keyword in lowered for keyword in vpn_keywords):
            matches.append(name)
    return matches


class NetworkUsageTracker:
    """Tracks cumulative bytes sent/received and reports the DELTA since the
    last call — that delta is what's meaningful to store per interval,
    not the ever-growing OS counter."""

    def __init__(self):
        self._last_sent = None
        self._last_recv = None
        self._last_time = None

    def sample(self) -> dict | None:
        counters = psutil.net_io_counters()
        now = time.time()

        if self._last_sent is None:
            # First call: nothing to diff against yet.
            self._last_sent, self._last_recv, self._last_time = counters.bytes_sent, counters.bytes_recv, now
            return None

        delta_sent = max(counters.bytes_sent - self._last_sent, 0)
        delta_recv = max(counters.bytes_recv - self._last_recv, 0)
        period_seconds = max(round(now - self._last_time), 1)

        self._last_sent, self._last_recv, self._last_time = counters.bytes_sent, counters.bytes_recv, now

        return {
            "network": {
                "bytesSent": delta_sent,
                "bytesReceived": delta_recv,
                "interfaceName": None,  # aggregate across all interfaces
            },
            "internet": {
                "uploadMb": round(delta_sent / (1024 * 1024), 3),
                "downloadMb": round(delta_recv / (1024 * 1024), 3),
                "periodSeconds": period_seconds,
            },
        }


def get_usb_devices_stub() -> list[dict]:
    """USB insert/remove detection needs OS-level hooks (WMI event
    subscriptions on Windows via pywin32). That's real deployment work
    against actual Windows hardware, not something testable in this
    sandbox — so this Phase 3 build ships the ingest endpoint
    (POST /monitoring/usb) and this stub, ready to be wired to real WMI
    events when the agent runs on an actual Windows lab PC."""
    return []
