from rest_framework import serializers

from .models import Agreement, FollowUpReport


class AgreementSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    mediationId = serializers.IntegerField(source="mediation_id", read_only=True)
    resolutionId = serializers.IntegerField(source="resolution_id", read_only=True)
    resolutionTitle = serializers.SerializerMethodField()
    preamble = serializers.CharField(read_only=True)
    agreedTerms = serializers.ListField(source="agreed_terms", child=serializers.CharField(), read_only=True)
    participantResponsibilities = serializers.JSONField(source="participant_responsibilities", read_only=True)
    milestones = serializers.JSONField(read_only=True)
    disputeEscalationClause = serializers.CharField(source="dispute_escalation_clause", read_only=True)
    signedAt = serializers.SerializerMethodField()
    signatures = serializers.JSONField(read_only=True)
    isFullySigned = serializers.SerializerMethodField()

    def get_resolutionTitle(self, obj):
        return obj.resolution.title if obj.resolution else ""

    def get_signedAt(self, obj):
        return obj.signed_at.isoformat() if obj.signed_at else None

    def get_isFullySigned(self, obj):
        required = obj.mediation.participants.exclude(status="INVITED").count()
        if required == 0:
            return False
        return len(obj.signatures or {}) >= required


class FinalizeRequestSerializer(serializers.Serializer):
    resolutionId = serializers.IntegerField(required=False, allow_null=True)


class FollowUpRequestSerializer(serializers.Serializer):
    sentiment = serializers.ChoiceField(choices=FollowUpReport.Sentiment.choices)
    comments = serializers.CharField(required=False, allow_blank=True, default="")
    reopen = serializers.BooleanField(required=False, default=False)
    participantId = serializers.IntegerField(required=False, allow_null=True)


class FollowUpReportSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    mediationId = serializers.IntegerField(source="mediation_id", read_only=True)
    participantId = serializers.IntegerField(source="participant_id", read_only=True)
    participantName = serializers.SerializerMethodField()
    sentiment = serializers.CharField(read_only=True)
    comments = serializers.CharField(read_only=True)
    timestamp = serializers.SerializerMethodField()
    reopenRequested = serializers.BooleanField(source="reopen_requested", read_only=True)

    def get_participantName(self, obj):
        return obj.participant.user.name or obj.participant.user.email

    def get_timestamp(self, obj):
        return obj.created_at.isoformat() if obj.created_at else None