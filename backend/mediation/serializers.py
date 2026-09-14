from rest_framework import serializers


class ParticipantSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    userId = serializers.SerializerMethodField()
    name = serializers.SerializerMethodField()
    email = serializers.SerializerMethodField()
    role = serializers.CharField(read_only=True)
    status = serializers.CharField(read_only=True)
    hasCompletedPrivateSession = serializers.BooleanField(source="has_completed_private_session", read_only=True)

    def get_userId(self, obj):
        return obj.user_id

    def get_name(self, obj):
        return obj.display_name

    def get_email(self, obj):
        return obj.contact_email


class MediationSummarySerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    title = serializers.CharField(read_only=True)
    description = serializers.CharField(read_only=True)
    category = serializers.CharField(read_only=True)
    status = serializers.CharField(read_only=True)
    inviteCode = serializers.SerializerMethodField()
    creatorId = serializers.SerializerMethodField()
    createdAt = serializers.SerializerMethodField()
    resolvedAt = serializers.SerializerMethodField()
    participants = ParticipantSerializer(many=True, read_only=True)

    def get_inviteCode(self, obj):
        return obj.invite_code

    def get_creatorId(self, obj):
        return obj.creator_id

    def get_createdAt(self, obj):
        return obj.created_at.isoformat() if obj.created_at else None

    def get_resolvedAt(self, obj):
        return obj.resolved_at.isoformat() if obj.resolved_at else None


class MediationDetailSerializer(MediationSummarySerializer):
    analysis = serializers.SerializerMethodField()
    proposals = serializers.SerializerMethodField()
    activeProposalId = serializers.SerializerMethodField()
    agreement = serializers.SerializerMethodField()
    followUps = serializers.SerializerMethodField()
    safetyAlert = serializers.SerializerMethodField()

    def get_analysis(self, obj):
        from resolutions.serializers import ConflictAnalysisSerializer
        analysis = getattr(obj, "_analysis", None)
        if analysis is None:
            return None
        return ConflictAnalysisSerializer(analysis).data

    def get_proposals(self, obj):
        from resolutions.serializers import ResolutionSerializer
        proposals = getattr(obj, "_proposals", None)
        if not proposals:
            return []
        return ResolutionSerializer(proposals, many=True).data

    def get_activeProposalId(self, obj):
        active = getattr(obj, "_active_resolution", None)
        return active.id if active else None

    def get_agreement(self, obj):
        from agreements.serializers import AgreementSerializer
        agreement = getattr(obj, "_agreement", None)
        if agreement is None:
            return None
        return AgreementSerializer(agreement).data

    def get_followUps(self, obj):
        from agreements.serializers import FollowUpReportSerializer
        reports = getattr(obj, "_followup_reports", None)
        if not reports:
            return []
        return FollowUpReportSerializer(reports, many=True).data

    def get_safetyAlert(self, obj):
        if not obj.safety_hold:
            return None
        return {
            "isHighRisk": True,
            "detectedRisks": ["Safety intervention activated"],
            "guidanceMessage": "Mediation has been paused due to safety concerns. Please contact professional support.",
            "emergencyResources": [],
        }


class InviteParticipantRequestSerializer(serializers.Serializer):
    name = serializers.CharField(max_length=150, required=False, allow_blank=True, default="")
    email = serializers.EmailField(required=False, allow_blank=True)
    role = serializers.CharField(max_length=80, required=False, allow_blank=True, default="Participant")


class JoinMediationRequestSerializer(serializers.Serializer):
    inviteCode = serializers.CharField(max_length=12)
    role = serializers.CharField(max_length=80, required=False, allow_blank=True, default="Participant")


class CreateMediationSerializer(serializers.Serializer):
    title = serializers.CharField(max_length=200, min_length=3)
    description = serializers.CharField(required=False, allow_blank=True, default="")
    category = serializers.CharField(required=False, allow_blank=True, default="General")
    participantNames = serializers.ListField(
        child=serializers.CharField(max_length=150, allow_blank=True),
        required=False,
        default=list,
    )
    participantEmails = serializers.ListField(
        child=serializers.EmailField(),
        required=False,
        default=list,
    )


class MediationStatusSerializer(serializers.Serializer):
    """Live dashboard payload: progress, participant states, votes, next step."""

    mediationId = serializers.IntegerField(source="pk", read_only=True)
    title = serializers.CharField(read_only=True)
    status = serializers.CharField(read_only=True)
    inviteCode = serializers.CharField(source="invite_code", read_only=True)
    safetyHold = serializers.BooleanField(source="safety_hold", read_only=True)
    phaseProgress = serializers.SerializerMethodField()
    participantStates = serializers.SerializerMethodField()
    proposalVotes = serializers.SerializerMethodField()
    nextStep = serializers.SerializerMethodField()

    def get_phaseProgress(self, obj):
        return {
            "caucusCompleted": obj.participants.filter(status__in=["IN_SESSION", "SESSION_COMPLETED"]).count(),
            "caucusTotal": obj.participants.exclude(status="INVITED").count(),
            "hasAnalysis": hasattr(obj, "analysis"),  # careful: not prefetched here
            "proposalsReady": obj.status == "PROPOSALS_READY",
            "negotiating": obj.status == "NEGOTIATING",
            "agreementSigned": obj.status in ("AGREEMENT_SIGNED", "FOLLOW_UP", "REOPENED", "RESOLVED", "CLOSED"),
        }

    def get_participantStates(self, obj):
        return [
            {
                "participantId": p.id,
                "name": p.user.name or p.user.email if p.user_id else p.name,
                "role": p.role,
                "status": p.status,
                "consentGiven": p.consent_given,
            }
            for p in obj.participants.all()
        ]

    def get_proposalVotes(self, obj):
        rows = obj.proposals.filter(is_active=True).prefetch_related("votes")
        return [
            {
                "id": r.id,
                "proposalNumber": r.proposal_number,
                "title": r.title,
                "refinementIteration": r.refinement_iteration,
                "votes": {str(v.participant_id): v.decision for v in r.votes.all()},
                "approved": all(v.decision == "ACCEPT" for v in r.votes.all()) and r.votes.exists(),
            }
            for r in rows
        ]

    def get_nextStep(self, obj):
        status = obj.status
        joined = obj.participants.filter(status__in=["JOINED", "IN_SESSION", "SESSION_COMPLETED"]).count()
        if obj.safety_hold:
            return "Safety check active: supportive review in progress. Continue when ready."
        if status == "CREATED":
            return "Invite the other participant to join using the invite code."
        if joined < 2:
            return "Waiting for at least two participants to join before analysis."
        if status == "IN_SESSION":
            return "Participants are completing their private caucuses."
        if status == "PROPOSALS_READY":
            return "Proposals are ready — review and vote on each one."
        if status == "NEGOTIATING":
            return "Review feedback and refine the proposal in dispute."
        if status in ("AGREEMENT_SIGNED", "FOLLOW_UP", "REOPENED", "RESOLVED", "CLOSED"):
            return "Agreement finalized. A 14-day follow-up check-in is scheduled."
        return "Continue in the app to advance the mediation."
