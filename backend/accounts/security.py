"""Security helpers: TOTP (MFA), email verification codes, password checks.

Implemented with only the standard library so no extra runtime dependencies are
required for production hardening.
"""

import base64
import hashlib
import hmac
import os
import secrets
import struct
import time
from datetime import timedelta

from django.core import exceptions
from django.utils import timezone

TOTP_PERIOD_SECONDS = 30
TOTP_DIGITS = 6
VERIFICATION_CODE_TTL = timedelta(hours=24)


def generate_verification_code(user) -> str:
    """Return a 6-digit code and store its SHA-256 hash (never the plaintext)."""
    code = f"{secrets.randbelow(1_000_000):06d}"
    user.verification_code_hash = _hash_code(code)
    user.verification_code_expires_at = timezone.now() + VERIFICATION_CODE_TTL
    user.save(update_fields=["verification_code_hash", "verification_code_expires_at"])
    return code


def verify_email_code(user, code: str) -> bool:
    if not code or not user.verification_code_hash:
        return False
    stored = user.verification_code_hash
    expires_at = user.verification_code_expires_at
    # The code is single-use: any attempt invalidates it, preventing brute force.
    user.verification_code_hash = ""
    user.verification_code_expires_at = None
    user.save(update_fields=["verification_code_hash", "verification_code_expires_at"])
    if expires_at is None or expires_at < timezone.now():
        return False
    if not hmac.compare_digest(stored, _hash_code(code)):
        return False
    user.email_verified = True
    user.save(update_fields=["email_verified"])
    return True


def _hash_code(code: str) -> str:
    return hashlib.sha256(code.encode()).hexdigest()


def generate_totp_secret() -> str:
    """Base32 random secret (160 bits) for a new TOTP enrollment."""
    return base64.b32encode(secrets.token_bytes(20)).decode().rstrip("=")


def totp_uri(secret_b32: str, account: str) -> str:
    issuer = "Mediara AI"
    secret = secret_b32
    return (
        f"otpauth://totp/{issuer}:{account}?secret={secret}"
        f"&issuer={issuer}&digits={TOTP_DIGITS}&period={TOTP_PERIOD_SECONDS}"
    )


def verify_totp(secret_b32: str, code: str) -> bool:
    try:
        code_int = int(code)
    except (TypeError, ValueError):
        return False
    if not (0 <= code_int < 10 ** TOTP_DIGITS):
        return False
    try:
        secret = base64.b32decode(secret_b32 + "=" * ((8 - len(secret_b32) % 8) % 8))
    except Exception:
        return False
    # Accept the current and the immediately-adjacent windows to tolerate clock skew.
    counter = int(time.time()) // TOTP_PERIOD_SECONDS
    for offset in (-1, 0, 1):
        if _totp_at(secret, counter + offset) == code_int:
            return True
    return False


def _totp_at(secret: bytes, counter: int) -> int:
    msg = struct.pack(">Q", counter)
    digest = hmac.new(secret, msg, hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    binary = struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF
    return binary % (10 ** TOTP_DIGITS)


def validate_password_strength(password: str) -> None:
    """Raise django.core.exceptions.ValidationError on weak passwords."""
    if len(password) < 8:
        raise exceptions.ValidationError("Password must be at least 8 characters long.")
    if not any(c.islower() for c in password) or not any(c.isupper() for c in password):
        raise exceptions.ValidationError("Password must contain both upper and lower case letters.")
    if not any(c.isdigit() for c in password):
        raise exceptions.ValidationError("Password must contain at least one number.")
    common = {"password", "password1", "12345678", "qwertyui", "1234qwer", "mediara123"}
    if password.lower() in common:
        raise exceptions.ValidationError("This password is too common.")