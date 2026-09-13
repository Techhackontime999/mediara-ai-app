from django.db import models


class Agreement(models.Model):
    mediation = models.OneToOneField("mediation.Mediation", on_delete=models.CASCADE, related_name="agreement")
    resolution = models.ForeignKey("resolutions.Resolution", on_delete=models.CASCADE, related_name="agreements")
    preamble = models.TextField(blank=True, default="")
    agreed_terms = models.JSONField(default=list, blank=True)
    participant_responsibilities = models.JSONField(default=dict, blank=True)
    milestones = models.JSONField(default=list, blank=True)
    dispute_escalation_clause = models.TextField(blank=True, default="")
    signatures = models.JSONField(default=dict, blank=True)  # participant_id -> iso timestamp
    signed_at = models.DateTimeField(null=True, blank=True)
    document_hash = models.CharField(max_length=64, blank=True, default="")  # SHA-256 of the canonical PDF bytes
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Agreement for {self.mediation.title}"


class FollowUpReport(models.Model):
    class Sentiment(models.TextChoices):
        WORKING = "WORKING", "Working"
        PARTIALLY_WORKING = "PARTIALLY_WORKING", "Partially Working"
        NOT_WORKING = "NOT_WORKING", "Not Working"

    mediation = models.ForeignKey("mediation.Mediation", on_delete=models.CASCADE, related_name="followup_reports")
    participant = models.ForeignKey("mediation.Participant", on_delete=models.CASCADE, related_name="followups")
    sentiment = models.CharField(max_length=24, choices=Sentiment.choices)
    comments = models.TextField(blank=True, default="")
    reopen_requested = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Follow-up {self.sentiment} by {self.participant}"
