"""Encryption-at-rest helpers.

Uses Fernet (AES-128-CBC + HMAC) from the `cryptography` package. A single
server-side key is supplied via the ENCRYPTION_KEY setting (base64 32-byte
Fernet key). If none is configured, a key is generated once and persisted to
BASE_DIR/entropy.key so local dev still works and survives restarts. In
production this key must come from the environment / secret manager.
"""

import base64
from pathlib import Path

from cryptography.fernet import Fernet, InvalidToken

_KEY_FILE_NAME = "entropy.key"


def resolve_encryption_key(configured: str, base_dir: Path) -> bytes:
    """Return a Fernet key: the configured one, or a generated one persisted for dev.

    Returns the key in its base64 text form (as required by ``cryptography.Fernet``):
    a 44/45-character url-safe base64 string bytes. `configured` is accepted as a
    base64 string; the persisted dev key is generated the same way.
    """
    if configured:
        try:
            Fernet(configured.encode("utf-8"))
            return configured.encode("utf-8")
        except Exception:
            raise ValueError("ENCRYPTION_KEY must be a base64-encoded Fernet key.")
    key_file = base_dir / _KEY_FILE_NAME
    if key_file.exists():
        raw = key_file.read_bytes().strip()
        try:
            Fernet(raw)
            return raw
        except Exception:
            pass
    key = Fernet.generate_key()
    key_file.write_bytes(key + b"\n")
    return key


def make_fernet(settings_module) -> Fernet:
    return Fernet(resolve_encryption_key(getattr(settings_module, "ENCRYPTION_KEY", ""), Path(getattr(settings_module, "BASE_DIR") or ".")))


def _fernet():
    from django.conf import settings
    return make_fernet(settings)


def encrypt_text(value: str) -> str:
    return _fernet().encrypt(value.encode("utf-8")).decode("ascii")


def decrypt_text(token: str) -> str:
    try:
        return _fernet().decrypt(token.encode("ascii")).decode("utf-8")
    except InvalidToken:
        raise


def backup_and_rotate_encryptor() -> None:
    """Placeholder for key-rotation tooling; documented for production use."""
    raise NotImplementedError("Key rotation must be added by the platform operator.")