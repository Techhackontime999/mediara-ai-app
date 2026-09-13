from mediation.models import Participant
from mediation.views import mediation_permission
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import ConflictAnalysis, Resolution, Vote
from .serializers import (
    ConflictAnalysisSerializer,
    RefineRequestSerializer,
    ResolutionSerializer,
    VoteRequestSerializer,
)
from .services import all_approved, cast_vote, refine_resolution, run_generate_resolutions


def _get_mediation(request, pk):
    from mediation.models import Mediation
    mediation = Mediation.objects.filter(pk=pk).first()
    if mediation is None:
        return None
    if not mediation_permission(request, mediation):
        return None
    return mediation


class AnalyzeView(APIView):
    """Run the full pipeline: perspectives -> analysis -> 3 proposals."""

    def post(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        try:
            resolutions = run_generate_resolutions(mediation)
        except ValueError as exc:
            return Response({"detail": str(exc)}, status=status.HTTP_400_BAD_REQUEST)
        from audit.models import AuditLog
        from notifications.services import notify_participants
        AuditLog.record(actor=request.user, action="analysis.generated", mediation=mediation,
                        entity_type="mediation", entity_id=mediation.pk, request=request)
        notify_participants(
            mediation,
            notification_type="analysis",
            title_template="Analysis and proposals are ready",
            body_template="Proposals are ready for review in '{title}'. Cast your votes.",
        )
        analysis = ConflictAnalysis.objects.filter(mediation=mediation).first()
        return Response(
            {
                "analysis": ConflictAnalysisSerializer(analysis).data,
                "proposals": ResolutionSerializer(resolutions, many=True).data,
            }
        )


class AnalysisDetailView(APIView):
    def get(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        analysis = ConflictAnalysis.objects.filter(mediation=mediation).first()
        if analysis is None:
            return Response({"detail": "Analysis not generated yet."}, status=status.HTTP_404_NOT_FOUND)
        return Response(ConflictAnalysisSerializer(analysis).data)


class GenerateResolutionsView(APIView):
    def post(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        try:
            resolutions = run_generate_resolutions(mediation)
        except ValueError as exc:
            return Response({"detail": str(exc)}, status=status.HTTP_400_BAD_REQUEST)
        return Response(ResolutionSerializer(resolutions, many=True).data)


class ResolutionListView(APIView):
    def get(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        resolutions = Resolution.objects.filter(mediation=mediation, is_active=True).prefetch_related("votes").order_by("proposal_number")
        return Response(ResolutionSerializer(resolutions, many=True).data)


class ResolutionVoteView(APIView):
    def post(self, request, pk):
        resolution = Resolution.objects.filter(pk=pk).prefetch_related("votes").first()
        if resolution is None:
            return Response({"detail": "Resolution not found."}, status=status.HTTP_404_NOT_FOUND)
        participant = Participant.objects.filter(mediation=resolution.mediation, user=request.user).first()
        if participant is None:
            return Response({"detail": "You are not a participant."}, status=status.HTTP_403_FORBIDDEN)

        serializer = VoteRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        cast_vote(resolution, participant, data["decision"], data.get("feedback", ""))
        resolution.refresh_from_db()
        from audit.models import AuditLog
        from notifications.services import notify_participants
        AuditLog.record(actor=request.user, action="proposal.voted", mediation=resolution.mediation,
                        entity_type="resolution", entity_id=resolution.pk, request=request,
                        decision=data["decision"], participant_id=participant.pk)
        if Vote.objects.filter(resolution=resolution).count() == 1:
            notify_participants(
                resolution.mediation,
                notification_type="vote",
                title_template="A participant has voted",
                body_template="Someone voted on a proposal in '{title}'. Check the newest result.",
            )

        # Determine if this mediation is fully accepted so the client can proceed.
        approved = all_approved(resolution)
        for res in Resolution.objects.filter(mediation=resolution.mediation):
            res.is_active = approved and res.id == resolution.id
            res.save(update_fields=["is_active"])

        return Response(
            {
                "resolution": ResolutionSerializer(resolution).data,
                "approvedByAll": approved,
            }
        )


class ResolutionRefineView(APIView):
    """POST /api/resolutions/{id}/refine/  -> refined Resolution (new row)."""

    def post(self, request, pk):
        resolution = Resolution.objects.filter(pk=pk).first()
        if resolution is None:
            return Response({"detail": "Resolution not found."}, status=status.HTTP_404_NOT_FOUND)
        participant = Participant.objects.filter(mediation=resolution.mediation, user=request.user).first()
        if participant is None:
            return Response({"detail": "You are not a participant."}, status=status.HTTP_403_FORBIDDEN)

        serializer = RefineRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        feedback = serializer.validated_data.get("feedbackSummary") or serializer.validated_data.get("feedback") or ""

        # Collect all outstanding feedback across votes to make refinement grounded.
        votes_with_feedback = Vote.objects.filter(resolution=resolution).exclude(feedback="")
        extra_feedback = "\n".join(f"- {v.feedback}" for v in votes_with_feedback)
        combined = (feedback + "\n" + extra_feedback).strip() or "Participants requested adjustments."

        refined = refine_resolution(resolution, combined)
        from audit.models import AuditLog
        from notifications.services import notify_participants
        AuditLog.record(actor=request.user, action="proposal.refined", mediation=resolution.mediation,
                        entity_type="resolution", entity_id=refined.pk, request=request)
        notify_participants(
            resolution.mediation,
            notification_type="refine",
            title_template="A proposal was refined",
            body_template="A proposal in '{title}' was updated after feedback. Review it again.",
        )
        return Response(ResolutionSerializer(refined).data, status=status.HTTP_201_CREATED)


class NegotiationStatusView(APIView):
    """GET /api/mediations/{id}/negotiation/ — current proposals + approval state."""

    def get(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        resolutions = Resolution.objects.filter(mediation=mediation, is_active=True).prefetch_related("votes").order_by("proposal_number")
        data = [ResolutionSerializer(r).data for r in resolutions]
        for item, res in zip(data, resolutions, strict=True):
            item["approvedByAll"] = all_approved(res)
        return Response(data)
