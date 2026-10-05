"""
Tests for the stable device identity added to the registration payload
(collector.get_machine_guid / collect_registration_payload).

Runs anywhere (including non-Windows CI) by injecting a fake `winreg`:

    python -m unittest test_collector_device_id -v
"""

import sys
import types
import unittest
from unittest import mock

import collector

GUID = "3F2504E0-4F89-11D3-9A0C-0305E82C3301"


def _fake_winreg(value=GUID, open_error=None, query_error=None):
    """Builds a stand-in for the Windows-only winreg module and records how
    it was called, so tests can assert the exact key/value/flags used."""
    module = types.ModuleType("winreg")
    module.HKEY_LOCAL_MACHINE = object()
    module.KEY_READ = 0x20019
    module.KEY_WOW64_64KEY = 0x0100
    module.calls = {}

    class _Key:
        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

    def open_key(hive, sub_key, reserved, access):
        module.calls["open"] = (hive, sub_key, reserved, access)
        if open_error:
            raise open_error
        return _Key()

    def query_value_ex(key, name):
        module.calls["query"] = name
        if query_error:
            raise query_error
        return value, 1

    module.OpenKey = open_key
    module.QueryValueEx = query_value_ex
    return module


class GetMachineGuidTests(unittest.TestCase):

    def test_reads_machine_guid_normalised(self):
        fake = _fake_winreg(value="  " + GUID + "  ")
        with mock.patch.dict(sys.modules, {"winreg": fake}):
            result = collector.get_machine_guid()
        self.assertEqual(result, GUID.lower())

    def test_reads_the_64bit_registry_view_of_the_right_key(self):
        fake = _fake_winreg()
        with mock.patch.dict(sys.modules, {"winreg": fake}):
            collector.get_machine_guid()
        hive, sub_key, reserved, access = fake.calls["open"]
        self.assertIs(hive, fake.HKEY_LOCAL_MACHINE)
        self.assertEqual(sub_key, r"SOFTWARE\Microsoft\Cryptography")
        self.assertEqual(reserved, 0)
        self.assertTrue(access & fake.KEY_WOW64_64KEY)
        self.assertTrue(access & fake.KEY_READ)
        self.assertEqual(fake.calls["query"], "MachineGuid")

    def test_missing_key_returns_none(self):
        fake = _fake_winreg(open_error=FileNotFoundError())
        with mock.patch.dict(sys.modules, {"winreg": fake}):
            self.assertIsNone(collector.get_machine_guid())

    def test_access_denied_returns_none(self):
        fake = _fake_winreg(query_error=PermissionError())
        with mock.patch.dict(sys.modules, {"winreg": fake}):
            self.assertIsNone(collector.get_machine_guid())

    def test_empty_or_non_string_value_returns_none(self):
        for bad in ("", "   ", None, 12345, b"bytes"):
            with self.subTest(value=bad):
                fake = _fake_winreg(value=bad)
                with mock.patch.dict(sys.modules, {"winreg": fake}):
                    self.assertIsNone(collector.get_machine_guid())

    def test_non_windows_without_winreg_returns_none(self):
        # A None entry in sys.modules makes `import winreg` raise ImportError.
        with mock.patch.dict(sys.modules, {"winreg": None}):
            self.assertIsNone(collector.get_machine_guid())


class RegistrationPayloadTests(unittest.TestCase):

    LEGACY_KEYS = {
        "hostname", "macAddress", "ipAddress", "osName", "osVersion",
        "cpuInfo", "ramMb", "diskGb", "agentVersion",
    }

    def test_payload_contains_device_id_when_available(self):
        with mock.patch.object(collector, "get_machine_guid", return_value=GUID.lower()):
            payload = collector.collect_registration_payload("1.0", None)
        self.assertEqual(payload["deviceId"], GUID.lower())
        self.assertTrue(self.LEGACY_KEYS.issubset(payload.keys()))

    def test_payload_omits_device_id_when_unavailable(self):
        with mock.patch.object(collector, "get_machine_guid", return_value=None):
            payload = collector.collect_registration_payload("1.0", None)
        self.assertNotIn("deviceId", payload)
        self.assertEqual(set(payload.keys()), self.LEGACY_KEYS)  # legacy shape unchanged

    def test_lab_id_behaviour_unchanged(self):
        with mock.patch.object(collector, "get_machine_guid", return_value=GUID.lower()):
            with_lab = collector.collect_registration_payload("1.0", "lab-1")
            without_lab = collector.collect_registration_payload("1.0", "")
        self.assertEqual(with_lab["labId"], "lab-1")
        self.assertNotIn("labId", without_lab)

    def test_machine_guid_is_not_read_for_heartbeats(self):
        with mock.patch.object(collector, "get_machine_guid",
                               side_effect=AssertionError("must not be read per heartbeat")):
            payload = collector.collect_heartbeat_payload()
        self.assertEqual(set(payload.keys()), {"cpuUsagePct", "ramUsagePct", "diskUsagePct", "ipAddress"})

    def test_machine_guid_is_not_read_for_network_sampling(self):
        tracker = collector.NetworkUsageTracker()
        with mock.patch.object(collector, "get_machine_guid",
                               side_effect=AssertionError("must not be read per monitoring cycle")):
            tracker.sample()
            sample = tracker.sample()
        self.assertEqual(set(sample["network"].keys()),
                         {"bytesSent", "bytesReceived", "interfaceName", "sampledAt"})


if __name__ == "__main__":
    unittest.main()
