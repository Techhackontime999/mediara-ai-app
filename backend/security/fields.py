"""Custom model field storing values encrypted at rest (Fernet)."""

from django.conf import settings
from django.db import models

from .crypto import encrypt_text

try:
    from .crypto import decrypt_text
except Exception:
    decrypt_text = None


class EncryptedTextField(models.TextField):
    """A TextField transparently encrypted with Fernet.

    Reads transparently decrypt. If a stored value is not valid Fernet (for
    example legacy plaintext rows), it is returned as-is to keep older data
    readable during migration.
    """

    def _encrypt(self, value):
        if value is None:
            return None
        return encrypt_text(str(value))

    def _decrypt(self, value):
        if value in (None, ""):
            return value
        try:
            return decrypt_text(value)
        except Exception:
            return value

    def get_prep_value(self, value):
        return self._encrypt(super().get_prep_value(value))

    def get_db_prep_value(self, value, connection, prepared=False):
        if not prepared:
            value = self.get_prep_value(value)
        return value

    def from_db_value(self, value, expression, connection):
        if value is None:
            return value
        return self._decrypt(value)

    def to_python(self, value):
        return self._decrypt(value) if isinstance(value, str) else None