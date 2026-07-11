"""
state_crypto.py — Encryption backends for agent_state.json.

Two backends, chosen automatically and recorded in the state file so a
decrypt attempt always knows which one produced the ciphertext:

  1. Windows DPAPI (via pywin32's win32crypt.CryptProtectData /
     CryptUnprotectData). Used whenever pywin32 is importable and the
     process is running on Windows. Ciphertext is tied to the local
     Windows user account — nothing else (no key file, no secret) is
     needed to decrypt it, and nothing else *can* decrypt it.

  2. cryptography.Fernet, with a key kept in a sibling file next to
     agent_state.json (permissions locked to the owner where the
     filesystem supports it). Used only when DPAPI isn't available
     (e.g. non-Windows dev/test machines, or pywin32 not installed).
     This is a materially weaker guarantee than DPAPI — the key lives
     on the same disk as the data it protects — so it exists purely to
     avoid plaintext-on-disk everywhere, not as a DPAPI-equivalent.

Callers (AgentState in agent.py) work with encrypt()/decrypt() on plain
bytes and never need to know which backend is active.
"""

import os
import sys

try:
    import win32crypt  # type: ignore
    import win32cryptcon  # type: ignore
except ImportError:
    win32crypt = None
    win32cryptcon = None

_DPAPI_DESCRIPTION = "SecureSOC agent state"
_FERNET_KEY_SUFFIX = ".key"


class DecryptionError(Exception):
    """Raised when stored ciphertext cannot be decrypted with the available
    backend. Messages must never include ciphertext/plaintext bytes or the
    decrypted token — callers log this exception's message directly."""


def _dpapi_available() -> bool:
    return sys.platform == "win32" and win32crypt is not None


class StateCrypto:
    """Encrypts/decrypts the agent_state.json payload. Bound to the state
    file's path only so the Fernet fallback can keep its key file next to
    it — DPAPI needs no extra file at all."""

    def __init__(self, state_path):
        self.state_path = state_path

    @property
    def active_method(self) -> str:
        return "dpapi" if _dpapi_available() else "fernet"

    def encrypt(self, plaintext: bytes) -> "tuple[str, bytes]":
        """Returns (method_name, ciphertext) using whichever backend is
        available now. method_name is persisted alongside the ciphertext
        so decrypt() later knows which backend to use."""
        method = self.active_method
        if method == "dpapi":
            ciphertext = win32crypt.CryptProtectData(
                plaintext, _DPAPI_DESCRIPTION, None, None, None,
                win32cryptcon.CRYPTPROTECT_UI_FORBIDDEN,
            )
        else:
            ciphertext = self._fernet_encrypt(plaintext)
        return method, ciphertext

    def decrypt(self, ciphertext: bytes, method: str) -> bytes:
        """Decrypts using the backend recorded in the file (`method`), not
        whatever backend happens to be available right now — so a mismatch
        (e.g. a DPAPI-encrypted file read on a non-Windows box) raises a
        clear DecryptionError instead of a confusing crash."""
        try:
            if method == "dpapi":
                if not _dpapi_available():
                    raise DecryptionError(
                        "State was encrypted with Windows DPAPI, which is unavailable on this machine."
                    )
                _description, data = win32crypt.CryptUnprotectData(
                    ciphertext, None, None, None,
                    win32cryptcon.CRYPTPROTECT_UI_FORBIDDEN,
                )
                return data
            if method == "fernet":
                return self._fernet_decrypt(ciphertext)
            raise DecryptionError(f"Unknown encryption method '{method}' recorded in state file.")
        except DecryptionError:
            raise
        except Exception as exc:
            # Collapse every backend-specific failure into one message that
            # never includes ciphertext/plaintext — only the exception type.
            raise DecryptionError(f"Decryption failed ({type(exc).__name__}).") from None

    # --- Fernet fallback (non-Windows / no pywin32) --------------------

    def _fernet_key_path(self):
        return self.state_path.with_name(self.state_path.stem + _FERNET_KEY_SUFFIX)

    def _fernet_key(self) -> bytes:
        from cryptography.fernet import Fernet

        key_path = self._fernet_key_path()
        if key_path.exists():
            return key_path.read_bytes()

        key = Fernet.generate_key()
        key_path.write_bytes(key)
        try:
            os.chmod(key_path, 0o600)  # no-op on platforms without POSIX perms
        except OSError:
            pass
        return key

    def _fernet_encrypt(self, plaintext: bytes) -> bytes:
        from cryptography.fernet import Fernet

        return Fernet(self._fernet_key()).encrypt(plaintext)

    def _fernet_decrypt(self, ciphertext: bytes) -> bytes:
        from cryptography.fernet import Fernet

        return Fernet(self._fernet_key()).decrypt(ciphertext)
