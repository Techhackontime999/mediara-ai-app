import secrets

from django.conf import settings
from django.db import models


class MediationStatus(models.TextChoices):
    CREATED = "CREATED", "Created"
    INVITED = "INVITED", "Invited"
    IN_PROGRESS = "IN_PROGRESS", "In Progress"
    ANALYZED = "ANALYZED", "Analyzed"
    PROPOSALS_READY = "PROPOSALS_READY", "Proposals Ready"
    NEGOTIATING = "NEGOTIATING", "Negotiating"
    RESOLVED = "RESOLVED", "Resolved"
    FOLLOW_UP = "FOLLOW_UP", "Follow Up"
    REOPENED = "REOPENED", "Reopened"
    CLOSED = "CLOSED", "Closed"
    SAFETY_HOLD = "SAFETY_HOLD", "Safety Hold"


class ParticipantStatus(models.TextChoices):
    INVITED = "INVITED", "Invited"
    JOINED = "JOINED", "Joined"
    IN_SESSION = "IN_SESSION", "In Session"
    SESSION_COMPLETED = "SESSION_COMPLETED", "Session Completed"
    REVIEWED = "REVIEWED", "Reviewed"
    SIGNED = "SIGNED", "Signed"


class Mediation(models.Model):
    title = models.CharField(max_length=200)
    description = models.TextField(blank=True, default="")
    category = models.CharField(max_length=60, blank=True, default="General")
    creator = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="created_mediations")
    status = models.CharField(max_length=32, choices=MediationStatus.choices, default=MediationStatus.CREATED)
    invite_code = models.CharField(max_length=12, unique=True, editable=False)
    created_at = models.DateTimeField(auto_now_add=True)
    resolved_at = models.DateTimeField(null=True, blank=True)
    safety_hold = models.BooleanField(default=False)
    # De-identified snapshot of the prior cycle, used to ground re-opened mediations.
    reopened_context = models.JSONField(default=dict, blank=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.title} ({self.invite_code})"

    def save(self, *args, **kwargs):
        if not self.invite_code:
            self.invite_code = self._generate_invite_code()
        super().save(*args, **kwargs)

    @staticmethod
    def _generate_invite_code() -> str:
        alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        while True:
            code = "".join(secrets.choice(alphabet) for _ in range(6))
            if not Mediation.objects.filter(invite_code=code).exists():
                return code

    @property
    def is_active(self) -> bool:
        return self.status not in {MediationStatus.RESOLVED, MediationStatus.CLOSED}

    @property
    def required_participants(self):
        return self.participants.exclude(status=ParticipantStatus.INVITED).count()

    @property
    def completed_participants(self):
        return self.participants.filter(status=ParticipantStatus.SESSION_COMPLETED).count()


class Participant(models.Model):
    mediation = models.ForeignKey(Mediation, on_delete=models.CASCADE, related_name="participants")
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="participations",
        null=True,
        blank=True,
    )
    # Denormalized contact info captured at invite time so a slot can exist
    # before the invited person registers or logs in with that email.
    email = models.EmailField(blank=True, null=True)
    name = models.CharField(max_length=150, blank=True, default="")
    role = models.CharField(max_length=80, blank=True, default="Participant")
    status = models.CharField(max_length=32, choices=ParticipantStatus.choices, default=ParticipantStatus.JOINED)
    consent_given = models.BooleanField(default=False)
    joined_at = models.DateTimeField(auto_now_add=True)
    has_completed_private_session = models.BooleanField(default=False)

    class Meta:
        ordering = ["joined_at"]
        constraints = [
            models.UniqueConstraint(fields=["mediation", "user"], name="unique_mediation_participant",
                                    condition=models.Q(user__isnull=False)),
        ]

    def __str__(self):
        who = self.display_name
        return f"{who} in {self.mediation.title}"

    @property
    def display_name(self):
        if self.user_id:
            return self.user.name or self.user.email or self.name
        return self.name or self.email or "Invited participant"

    @property
    def contact_email(self):
        if self.user_id:
            return self.email or self.user.email
        return self.email
