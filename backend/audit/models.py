"""Immutable audit trail for trust-sensitive events.

Every consequential action (auth, joining, voting, signing, follow-up, data
erasure) is recorded here. Rows are append-only: moderation and export features
rely on them instead of trusting live mutable state.
"""

from django.conf import settings
from django.db import models


class AuditLog(models.Model):
    """Append-only record of a consequential action. Never edited or deleted."""

    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="audit_events",
    )
    action = models.CharField(max_length=80)  # e.g. "mediation.join", "resolution.vote"
    entity_type = models.CharField(max_length=60, blank=True, default="")
    entity_id = models.CharField(max_length=40, blank=True, default="")
    mediation = models.ForeignKey(
        "mediation.Mediation",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="audit_events",
    )
    metadata = models.JSONField(default=dict, blank=True)
    ip_address = models.GenericIPAddressField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["actor", "-created_at"]),
            models.Index(fields=["mediation", "-created_at"]),
            models.Index(fields=["action"]),
        ]

    def __str__(self):
        return f"[{self.created_at:%Y-%m-%d %H:%M}] {self.action} by {self.actor or 'anonymous'}"

    @staticmethod
    def record(*, actor=None, action, mediation=None, entity_type="", entity_id="", request=None, **metadata):
        """Write an audit row. Metadata values are coerced to JSON-safe primitives."""
        ip = None
        if request is not None:
            ip = getattr(request, "client_ip", None) or request.META.get("REMOTE_ADDR")
        safe = {}
        for key, value in metadata.items():
            if isinstance(value, models.Model):
                value = str(getattr(value, "pk", value))
            if isinstance(value, (dict, list)):
                safe[key] = value
            else:
                safe[key] = str(value)
        AuditLog.objects.create(
            actor_id=actor.pk if hasattr(actor, "pk") and actor.pk else None,
            action=action,
            mediation=mediation,
            entity_type=entity_type,
            entity_id=str(entity_id) if entity_id is not None else "",
            metadata=safe,
            ip_address=ip,
        )
