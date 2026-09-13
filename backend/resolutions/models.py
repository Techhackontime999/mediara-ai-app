from django.db import models


class Perspective(models.Model):
    """Structured, de-identified participant perspective. Always kept private."""

    mediation = models.ForeignKey("mediation.Mediation", on_delete=models.CASCADE, related_name="perspectives")
    participant = models.ForeignKey("mediation.Participant", on_delete=models.CASCADE, related_name="perspective")

    goals = models.JSONField(default=list, blank=True)
    concerns = models.JSONField(default=list, blank=True)
    needs = models.JSONField(default=list, blank=True)
    constraints = models.JSONField(default=list, blank=True)
    emotions = models.JSONField(default=list, blank=True)
    desired_outcome = models.TextField(blank=True, default="")
    acceptable_compromises = models.JSONField(default=list, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["mediation", "participant"], name="unique_participant_perspective"),
        ]

    def __str__(self):
        return f"Perspective of {self.participant}"


class ConflictAnalysis(models.Model):
    mediation = models.OneToOneField("mediation.Mediation", on_delete=models.CASCADE, related_name="analysis")

    common_goals = models.JSONField(default=list, blank=True)
    conflicting_goals = models.JSONField(default=list, blank=True)
    root_causes = models.JSONField(default=list, blank=True)
    misunderstandings = models.JSONField(default=list, blank=True)
    emotional_factors = models.JSONField(default=list, blank=True)
    expectation_gaps = models.JSONField(default=list, blank=True)
    non_negotiable_concerns = models.JSONField(default=list, blank=True)
    potential_compromises = models.JSONField(default=list, blank=True)
    common_ground_summary = models.TextField(blank=True, default="")
    compatibility_score = models.IntegerField(default=75)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Analysis of {self.mediation.title}"


class Resolution(models.Model):
    """A resolution proposal. Refinement creates a new Resolution row."""

    mediation = models.ForeignKey("mediation.Mediation", on_delete=models.CASCADE, related_name="proposals")
    proposal_number = models.IntegerField(default=1)
    title = models.CharField(max_length=200)
    model_type = models.CharField(max_length=80, blank=True, default="")
    description = models.TextField(blank=True, default="")
    benefits = models.JSONField(default=list, blank=True)
    tradeoffs = models.JSONField(default=list, blank=True)
    required_compromises = models.JSONField(default=dict, blank=True)
    expected_impact = models.TextField(blank=True, default="")
    why_it_works = models.TextField(blank=True, default="")
    grounded_in = models.JSONField(default=list, blank=True)
    refinement_iteration = models.IntegerField(default=1)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["proposal_number", "-refinement_iteration", "-created_at"]

    def __str__(self):
        return f"#{self.proposal_number} {self.title}"


class Vote(models.Model):
    class Decision(models.TextChoices):
        ACCEPT = "ACCEPT", "Accept"
        REQUEST_CHANGES = "REQUEST_CHANGES", "Request Changes"
        REJECT = "REJECT", "Reject"

    resolution = models.ForeignKey(Resolution, on_delete=models.CASCADE, related_name="votes")
    participant = models.ForeignKey("mediation.Participant", on_delete=models.CASCADE, related_name="votes")
    decision = models.CharField(max_length=24, choices=Decision.choices)
    feedback = models.TextField(blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["resolution", "participant"], name="unique_participant_vote"),
        ]

    def __str__(self):
        return f"{self.participant} -> {self.decision}"
