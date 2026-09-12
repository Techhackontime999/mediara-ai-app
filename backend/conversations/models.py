from django.db import models

from mediation.models import Mediation, Participant
from security.fields import EncryptedTextField


class SenderType(models.TextChoices):
    USER = "USER", "User"
    AI_MEDIATOR = "AI_MEDIATOR", "AI Mediator"
    SYSTEM = "SYSTEM", "System"


class Conversation(models.Model):
    """Private 1-on-1 AI session for exactly one participant per mediation."""

    mediation = models.ForeignKey(Mediation, on_delete=models.CASCADE, related_name="conversations")
    participant = models.ForeignKey(Participant, on_delete=models.CASCADE, related_name="conversation")
    is_private = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["mediation", "participant"], name="unique_participant_conversation"),
        ]

    def __str__(self):
        return f"Private session: {self.participant}"


class Message(models.Model):
    conversation = models.ForeignKey(Conversation, on_delete=models.CASCADE, related_name="messages")
    sender = models.CharField(max_length=20, choices=SenderType.choices, default=SenderType.USER)
    content = EncryptedTextField()  # encrypted at rest (Fernet)
    message_type = models.CharField(max_length=30, blank=True, default="TEXT")
    risk_flagged = models.BooleanField(default=False)
    risk_categories = models.JSONField(default=list, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["created_at"]