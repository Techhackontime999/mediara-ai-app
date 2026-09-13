from rest_framework import serializers

from .models import Vote


class PerspectiveSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    mediationId = serializers.IntegerField(source="mediation_id", read_only=True)
    participantId = serializers.IntegerField(source="participant_id", read_only=True)
    participantName = serializers.SerializerMethodField()
    goals = serializers.ListField(child=serializers.CharField(), read_only=True)
    concerns = serializers.ListField(child=serializers.CharField(), read_only=True)
    needs = serializers.ListField(child=serializers.CharField(), read_only=True)
    constraints = serializers.ListField(child=serializers.CharField(), read_only=True)
    emotions = serializers.ListField(child=serializers.CharField(), read_only=True)
    desiredOutcome = serializers.CharField(source="desired_outcome", read_only=True)
    acceptableCompromises = serializers.ListField(source="acceptable_compromises", child=serializers.CharField(), read_only=True)

    def get_participantName(self, obj):
        return obj.participant.user.name or obj.participant.user.email


class ConflictAnalysisSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    mediationId = serializers.IntegerField(source="mediation_id", read_only=True)
    commonGoals = serializers.SerializerMethodField()
    conflictingGoals = serializers.SerializerMethodField()
    rootCauses = serializers.SerializerMethodField()
    misunderstandings = serializers.SerializerMethodField()
    emotionalFactors = serializers.SerializerMethodField()
    expectationGaps = serializers.SerializerMethodField()
    nonNegotiableConcerns = serializers.SerializerMethodField()
    potentialCompromises = serializers.SerializerMethodField()
    commonGroundSummary = serializers.CharField(source="common_ground_summary", read_only=True)
    compatibilityScore = serializers.IntegerField(source="compatibility_score", read_only=True)
    analyzedAt = serializers.SerializerMethodField()

    def _ref(self, obj, field):
        return getattr(obj, field, []) or []

    def get_commonGoals(self, obj):
        return self._ref(obj, "common_goals")

    def get_conflictingGoals(self, obj):
        return self._ref(obj, "conflicting_goals")

    def get_rootCauses(self, obj):
        return self._ref(obj, "root_causes")

    def get_misunderstandings(self, obj):
        return self._ref(obj, "misunderstandings")

    def get_emotionalFactors(self, obj):
        return self._ref(obj, "emotional_factors")

    def get_expectationGaps(self, obj):
        return self._ref(obj, "expectation_gaps")

    def get_nonNegotiableConcerns(self, obj):
        return self._ref(obj, "non_negotiable_concerns")

    def get_potentialCompromises(self, obj):
        return self._ref(obj, "potential_compromises")

    def get_analyzedAt(self, obj):
        return obj.created_at.isoformat() if obj.created_at else None


class ResolutionSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    proposalNumber = serializers.IntegerField(source="proposal_number", read_only=True)
    title = serializers.CharField(read_only=True)
    modelType = serializers.CharField(source="model_type", read_only=True)
    description = serializers.CharField(read_only=True)
    benefits = serializers.ListField(child=serializers.CharField(), read_only=True)
    tradeoffs = serializers.ListField(child=serializers.CharField(), read_only=True)
    requiredCompromises = serializers.JSONField(source="required_compromises", read_only=True)
    expectedImpact = serializers.CharField(source="expected_impact", read_only=True)
    whyItWorks = serializers.CharField(source="why_it_works", read_only=True)
    groundedIn = serializers.JSONField(source="grounded_in", read_only=True)
    votes = serializers.SerializerMethodField()
    feedback = serializers.SerializerMethodField()
    isApproved = serializers.SerializerMethodField()
    refinementIteration = serializers.IntegerField(source="refinement_iteration", read_only=True)

    def get_votes(self, obj):
        votes = obj.votes.all()
        return {str(v.participant_id): v.decision for v in votes}

    def get_feedback(self, obj):
        votes = obj.votes.all()
        return {str(v.participant_id): v.feedback for v in votes if v.feedback}

    def get_isApproved(self, obj):
        votes = obj.votes.all()
        if not votes:
            return False
        return all(v.decision == Vote.Decision.ACCEPT for v in votes) and \
            obj.mediation.participants.filter(status__in=["JOINED", "IN_SESSION", "SESSION_COMPLETED", "REVIEWED", "SIGNED"]).count() > 0


class VoteRequestSerializer(serializers.Serializer):
    decision = serializers.ChoiceField(choices=Vote.Decision.choices)
    feedback = serializers.CharField(required=False, allow_blank=True, default="")
    participantId = serializers.IntegerField(required=False, allow_null=True)


class RefineRequestSerializer(serializers.Serializer):
    feedback = serializers.CharField(required=False, allow_blank=True, default="")
    feedbackSummary = serializers.CharField(required=False, allow_blank=True, default="")
