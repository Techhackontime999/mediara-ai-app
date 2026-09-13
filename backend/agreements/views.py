from datetime import UTC, datetime

from audit.models import AuditLog
from django.http import HttpResponse
from mediation.models import Mediation, MediationStatus, Participant
from mediation.views import mediation_permission
from notifications.services import create_notification
from resolutions.models import Resolution
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Agreement, FollowUpReport
from .pdf import agreement_sha256, build_agreement_pdf_bytes
from .serializers import (
    AgreementSerializer,
    FinalizeRequestSerializer,
    FollowUpReportSerializer,
    FollowUpRequestSerializer,
)

DEFAULT_ESCALATION = (
    "If friction or discrepancy re-emerges, parties agree to re-open Mediara AI "
    "mediation before taking external action."
)


def _get_mediation(request, pk):
    mediation = Mediation.objects.filter(pk=pk).select_related("agreement").prefetch_related(
        "participants__user", "proposals", "analysis"
    ).first()
    if mediation is None:
        return None
    if not mediation_permission(request, mediation):
        return None
    return mediation


def _build_agreement(mediation: Mediation, resolution: Resolution) -> Agreement:
    participants = list(mediation.participants.exclude(status="INVITED").select_related("user"))

    responsibilities = {}
    for p in participants:
        name = p.user.name or p.user.email
        comp = resolution.required_compromises.get(name) or resolution.required_compromises.get(p.user.email)
        responsibilities[p.user.email] = [
            comp or f"Uphold the '{resolution.title}' terms and respectful communication standards.",
            "Attend the scheduled 14-day check-in to review agreement efficacy.",
            "Raise concerns through declared communication channels before escalation.",
        ]

    terms = [
        f"Adoption of '{resolution.title}': {resolution.description}",
        "Mutual commitment to respectful dialogue and a 24-hour cooling-off period during disagreements.",
        "Equal recognition of each participant's autonomy and well-being.",
        "Agreement to formal 14-day and 30-day Mediara AI follow-up milestones.",
    ]

    milestones = [
        {"title": "Initial Operational Calibration", "dueDate": "Day 7", "assignedTo": "All Parties"},
        {"title": "Formal 14-Day Efficacy Check-in", "dueDate": "Day 14", "assignedTo": "All Parties"},
        {"title": "Permanent Protocol Ratification", "dueDate": "Day 30", "assignedTo": "All Parties"},
    ]

    now = datetime.now(UTC)
    signatures = {str(p.id): now.isoformat() for p in participants}

    agreement, _ = Agreement.objects.update_or_create(
        mediation=mediation,
        defaults={
            "resolution": resolution,
            "preamble": "This accord embodies the mutual good-faith resolution reached between parties facilitated by Mediara AI.",
            "agreed_terms": terms,
            "participant_responsibilities": responsibilities,
            "milestones": milestones,
            "dispute_escalation_clause": DEFAULT_ESCALATION,
            "signatures": signatures,
            "signed_at": now,
        },
    )

    # Record the canonical SHA-256 fingerprint of the generated PDF. Because the
    # layout is deterministic, every later regeneration yields identical bytes.
    agreement.document_hash = agreement_sha256(build_agreement_pdf_bytes(mediation, agreement))
    agreement.save(update_fields=["document_hash"])
    return agreement


class FinalizeMediationView(APIView):
    """Create a Mutual Agreement from an approved resolution and resolve the mediation."""

    def post(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)

        serializer = FinalizeRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        resolution_id = serializer.validated_data.get("resolutionId")

        if resolution_id:
            resolution = Resolution.objects.filter(pk=resolution_id, mediation=mediation).first()
        else:
            resolution = Resolution.objects.filter(mediation=mediation, is_active=True).order_by("proposal_number").first()
        if resolution is None:
            return Response({"detail": "No approved resolution to finalize."}, status=status.HTTP_400_BAD_REQUEST)

        from resolutions.services import all_approved
        if not all_approved(resolution):
            return Response(
                {"detail": "Not all participants have accepted a proposal yet."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        agreement = _build_agreement(mediation, resolution)
        mediation.status = MediationStatus.RESOLVED
        mediation.resolved_at = datetime.now(UTC)
        mediation.save(update_fields=["status", "resolved_at"])

        for participant in mediation.participants.exclude(status="INVITED"):
            participant.status = "SIGNED"
            participant.save(update_fields=["status"])

        AuditLog.record(
            actor=request.user,
            action="agreement.finalized",
            mediation=mediation,
            entity_type="agreement",
            entity_id=agreement.pk,
            request=request,
        )
        for participant in mediation.participants.exclude(status="INVITED"):
            create_notification(
                user=participant.user,
                notification_type="agreement",
                title="Your resolution accord is ready",
                body=f"The mutual accord for '{mediation.title}' is ratified and the official PDF is available.",
                mediation=mediation,
            )
        return Response(AgreementSerializer(agreement).data)


class AgreementDetailView(APIView):
    def get(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        agreement = getattr(mediation, "agreement", None)
        if agreement is None:
            return Response({"detail": "No agreement yet."}, status=status.HTTP_404_NOT_FOUND)
        return Response(AgreementSerializer(agreement).data)


class AgreementPdfView(APIView):
    def get(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return HttpResponse(status=403)
        agreement = getattr(mediation, "agreement", None)
        if agreement is None:
            return HttpResponse(status=404)
        pdf_bytes = build_agreement_pdf_bytes(mediation, agreement)
        response = HttpResponse(pdf_bytes, content_type="application/pdf")
        response["Content-Disposition"] = f'attachment; filename="Mediara_Agreement_{mediation.invite_code}.pdf"'
        return response


class AgreementHashView(APIView):
    """GET /api/agreements/{id}/hash/ — the canonical SHA-256 fingerprint."""

    def get(self, request, pk):
        agreement = Agreement.objects.filter(pk=pk).select_related("mediation").first()
        if agreement is None or not mediation_permission(request, agreement.mediation):
            return Response({"detail": "Agreement not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        return Response({
            "agreementId": agreement.pk,
            "mediationId": agreement.mediation_id,
            "sha256": agreement.document_hash or "",
            "recordedAt": agreement.signed_at.isoformat() if agreement.signed_at else None,
        })


class AgreementVerifyView(APIView):
    """POST /api/agreements/{id}/verify/ — {sha256} proves a held PDF is authentic & unaltered."""

    def post(self, request, pk):
        agreement = Agreement.objects.filter(pk=pk).select_related("mediation").first()
        if agreement is None or not mediation_permission(request, agreement.mediation):
            return Response({"detail": "Agreement not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        submitted = (request.data.get("sha256") or "").strip().lower()
        stored = (agreement.document_hash or "").lower()
        matches = bool(stored) and submitted == stored
        AuditLog.record(
            actor=request.user,
            action="agreement.verify",
            mediation=agreement.mediation,
            entity_type="agreement",
            entity_id=agreement.pk,
            request=request,
            matches="true" if matches else "false",
        )
        return Response({
            "storedHash": stored,
            "providedHash": submitted,
            "matches": matches,
            "detail": "Authentic and unaltered." if matches else "Hash does not match the platform record.",
        })


class FollowUpSubmitView(APIView):
    def post(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)

        serializer = FollowUpRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        participant = Participant.objects.filter(mediation=mediation, user=request.user).first()
        if participant is None:
            return Response({"detail": "You are not a participant."}, status=status.HTTP_403_FORBIDDEN)

        reopen = data.get("reopen") or data["sentiment"] == FollowUpReport.Sentiment.NOT_WORKING
        report = FollowUpReport.objects.create(
            mediation=mediation,
            participant=participant,
            sentiment=data["sentiment"],
            comments=data.get("comments", ""),
            reopen_requested=reopen,
        )

        mediation.status = MediationStatus.REOPENED if reopen else MediationStatus.FOLLOW_UP
        mediation.save(update_fields=["status"])

        AuditLog.record(
            actor=request.user,
            action="followup.submitted",
            mediation=mediation,
            entity_type="followup",
            entity_id=report.pk,
            request=request,
            sentiment=report.sentiment,
            reopen="true" if reopen else "false",
        )

        if reopen:
            _snapshot_reopened_context(mediation)
            for participant in mediation.participants.exclude(status="INVITED").exclude(user=request.user):
                create_notification(
                    user=participant.user,
                    notification_type="reopen",
                    title="Mediation re-opened by a participant",
                    body=f"A participant marked '{mediation.title}' as not working. A new AI adjustment cycle has started.",
                    mediation=mediation,
                )
        return Response(FollowUpReportSerializer(report).data, status=status.HTTP_201_CREATED)


class FollowUpListView(APIView):
    def get(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        reports = mediation.followup_reports.select_related("participant__user").order_by("-created_at")
        return Response(FollowUpReportSerializer(reports, many=True).data)


class ReopenMediationView(APIView):
    def post(self, request, pk):
        mediation = _get_mediation(request, pk)
        if mediation is None:
            return Response({"detail": "Mediation not found or forbidden."}, status=status.HTTP_404_NOT_FOUND)
        mediation.status = MediationStatus.REOPENED
        mediation.safety_hold = False
        mediation.save(update_fields=["status", "safety_hold"])
        _snapshot_reopened_context(mediation)
        AuditLog.record(actor=request.user, action="mediation.reopened", mediation=mediation,
                        entity_type="mediation", entity_id=mediation.pk, request=request)
        return Response({"detail": "Mediation reopened."})


def _snapshot_reopened_context(mediation):
    """Persist prior analysis + proposals so a reopened cycle stays grounded.

    Data is stored de-identified (no participant private content) so the next
    AI pass can reference what actually failed without re-reading raw caucuses.
    """
    from resolutions.models import ConflictAnalysis, Resolution

    analysis = ConflictAnalysis.objects.filter(mediation=mediation).first()
    resolutions = list(Resolution.objects.filter(mediation=mediation).order_by("proposal_number"))

    prior = {
        "title": mediation.title,
        "category": mediation.category,
        "common_ground_summary": analysis.common_ground_summary if analysis else "",
        "compatibility_score": analysis.compatibility_score if analysis else None,
        "root_causes": list(analysis.root_causes) if analysis else [],
        "proposal_titles": [r.title for r in resolutions],
        "accepted_proposal": next((r.title for r in resolutions if r.is_active and all(
            v.decision == "ACCEPT" for v in r.votes.all()
        )), None),
    }
    mediation.reopened_context = prior
    mediation.save(update_fields=["reopened_context"])
