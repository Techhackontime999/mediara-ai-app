from django.contrib.auth import get_user_model
from django.db import transaction
from django.db.models import Q
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from audit.models import AuditLog
from notifications.services import create_notification
from .models import Mediation, MediationStatus, Participant, ParticipantStatus
from .serializers import (
    CreateMediationSerializer,
    InviteParticipantRequestSerializer,
    JoinMediationRequestSerializer,
    MediationDetailSerializer,
    MediationSummarySerializer,
    ParticipantSerializer,
    MediationStatusSerializer,
)

User = get_user_model()


def mediation_permission(request, mediation: Mediation) -> bool:
    """Only participants (or the creator who is also a participant) may view it."""
    return Participant.objects.filter(mediation=mediation, user=request.user).exists()


class MediationListCreateView(APIView):
    def get(self, request):
        participants = Participant.objects.filter(user=request.user).select_related("mediation")
        mediation_ids = [p.mediation_id for p in participants]
        qs = Mediation.objects.filter(
            Q(id__in=mediation_ids) | Q(creator=request.user)
        ).select_related("creator").prefetch_related(
            "participants__user"
        ).distinct()
        return Response(MediationSummarySerializer(qs, many=True).data)

    def post(self, request):
        serializer = CreateMediationSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        with transaction.atomic():
            mediation = Mediation.objects.create(
                title=data["title"],
                description=data["description"],
                category=data["category"],
                creator=request.user,
            )
            Participant.objects.create(
                mediation=mediation,
                user=request.user,
                role="Initiator",
                status=ParticipantStatus.JOINED,
                consent_given=True,
            )

            invited_names = [n for n in data["participantNames"] if n.strip()]
            invited_emails = [e for e in data["participantEmails"] if e.strip()]

            # Invite by email when supplied (slot can be claimed on join by the
            # person who registers with that email). Otherwise a name placeholder.
            for i, name in enumerate(invited_names):
                self._add_participant(mediation, name=name.strip(), email=None, fallback_role=f"Participant {chr(66 + i) if i < 24 else 'P'}")
            for email in invited_emails:
                self._add_participant(mediation, name="", email=email, fallback_role="Participant")

        mediation = Mediation.objects.prefetch_related("participants__user").get(pk=mediation.pk)
        if mediation.participants.count() > 1 and mediation.status == MediationStatus.CREATED:
            mediation.status = MediationStatus.INVITED
            mediation.save(update_fields=["status"])

        AuditLog.record(actor=request.user, action="mediation.created", mediation=mediation,
                        entity_type="mediation", entity_id=mediation.pk, request=request)
        for participant in mediation.participants.exclude(status=ParticipantStatus.INVITED).exclude(user=request.user):
            create_notification(
                participant.user,
                notification_type="invite",
                title="You were invited to a mediation",
                body=request.user.name + f" invited you to '{mediation.title}'. Join with invite code {mediation.invite_code}.",
                mediation=mediation,
            )
        return Response(MediationDetailSerializer(mediation).data, status=status.HTTP_201_CREATED)

    def _add_participant(self, mediation, name, email, fallback_role):
        linked_user = User.objects.filter(email__iexact=email).first() if email else None

        # Deduplicate against an existing slot with the same email or name.
        existing = None
        if email:
            existing = Participant.objects.filter(mediation=mediation, email__iexact=email).first()
        if existing is None and name:
            existing = Participant.objects.filter(mediation=mediation, name__iexact=name).exclude(user__isnull=False).first()
        if existing:
            if linked_user and existing.user_id is None:
                existing.user = linked_user
                existing.status = ParticipantStatus.JOINED
                existing.save(update_fields=["user_id", "status"])
            return

        Participant.objects.create(
            mediation=mediation,
            user=linked_user,
            name=name,
            email=email,
            role=fallback_role,
            status=ParticipantStatus.INVITED if linked_user is None else ParticipantStatus.JOINED,
        )


class MediationByCodeView(APIView):
    """Look up a mediation by its invite code (used before joining)."""

    permission_classes = [AllowAny]

    def get(self, request, code):
        mediation = Mediation.objects.filter(invite_code=code.upper().strip()).prefetch_related("participants__user").first()
        if mediation is None:
            return Response({"detail": "No mediation found for this invite code."}, status=status.HTTP_404_NOT_FOUND)
        return Response(MediationSummarySerializer(mediation).data)


class MediationDetailView(APIView):
    def get(self, request, pk):
        mediation = Mediation.objects.filter(pk=pk).prefetch_related(
            "participants__user",
        ).first()
        if mediation is None:
            return Response({"detail": "Mediation not found."}, status=status.HTTP_404_NOT_FOUND)
        if not mediation_permission(request, mediation):
            return Response({"detail": "You are not a participant of this mediation."}, status=status.HTTP_403_FORBIDDEN)
        return Response(MediationDetailSerializer(mediation).data)


class MediationInviteView(APIView):
    def post(self, request, pk):
        mediation = Mediation.objects.filter(pk=pk).first()
        if mediation is None:
            return Response({"detail": "Mediation not found."}, status=status.HTTP_404_NOT_FOUND)
        if mediation.creator_id != request.user.id:
            return Response({"detail": "Only the creator can invite participants."}, status=status.HTTP_403_FORBIDDEN)

        serializer = InviteParticipantRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        email = (data.get("email") or "").lower().strip()
        role = data.get("role") or "Participant"

        # Link an existing account if this user already registered.
        linked_user = User.objects.filter(email__iexact=email).first() if email else None

        existing = None
        if email:
            existing = Participant.objects.filter(mediation=mediation, email__iexact=email).first()
        if existing is None and data.get("name"):
            existing = Participant.objects.filter(mediation=mediation, name__iexact=data["name"]).exclude(user__isnull=False).first()

        if existing:
            participant = existing
            participant.role = role or participant.role
            if linked_user and participant.user_id is None:
                participant.user = linked_user
                participant.status = ParticipantStatus.JOINED
            participant.save()
        else:
            participant = Participant.objects.create(
                mediation=mediation,
                user=linked_user,
                name=data.get("name", ""),
                email=email,
                role=role,
                status=ParticipantStatus.INVITED if linked_user is None else ParticipantStatus.JOINED,
            )

        if mediation.status == MediationStatus.CREATED:
            mediation.status = MediationStatus.INVITED
            mediation.save(update_fields=["status"])
        AuditLog.record(actor=request.user, action="mediation.invited", mediation=mediation,
                        entity_type="participant", entity_id=participant.pk, request=request,
                        email=participant.contact_email or "")
        if participant.user_id:
            create_notification(
                participant.user,
                notification_type="invite",
                title="You were invited to a mediation",
                body=f"Join '{mediation.title}' with invite code {mediation.invite_code}.",
                mediation=mediation,
            )
        return Response(ParticipantSerializer(participant).data)


class MediationJoinView(APIView):
    def post(self, request):
        serializer = JoinMediationRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        code = data["inviteCode"].upper().strip()
        mediation = Mediation.objects.filter(invite_code=code).first()
        if mediation is None:
            return Response({"detail": "No mediation found for this invite code."}, status=status.HTTP_404_NOT_FOUND)
        if mediation.status == MediationStatus.RESOLVED or mediation.status == MediationStatus.CLOSED:
            return Response({"detail": "This mediation has already been resolved."}, status=status.HTTP_400_BAD_REQUEST)

        already = Participant.objects.filter(mediation=mediation, user=request.user).first()

        if already:
            participant = already
        else:
            # Bind to an unclaimed slot: prefer one invited under this email,
            # then any remaining unclaimed (name-only) slot.
            participant = (
                Participant.objects.filter(mediation=mediation, user__isnull=True, email__iexact=request.user.email)
                .first()
                or Participant.objects.filter(mediation=mediation, user__isnull=True)
                .order_by("joined_at")
                .first()
            )
            if participant is None:
                # Multi-party open slot: anyone with the code may join new
                # participants up to a cap.
                if mediation.participants.count() >= 12:
                    return Response(
                        {"detail": "This mediation already has the maximum number of participants."},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                participant = Participant.objects.create(
                    mediation=mediation,
                    user=request.user,
                    name=request.user.name or request.user.email,
                    email=request.user.email,
                    role=data["role"] or "Participant",
                    status=ParticipantStatus.JOINED,
                    consent_given=True,
                )
            else:
                participant.user = request.user
                participant.email = request.user.email
                participant.name = request.user.name or request.user.email
                participant.status = ParticipantStatus.JOINED
                participant.consent_given = True
                participant.role = data["role"] or participant.role
                participant.save()

        joined_count = mediation.participants.filter(status__in=[ParticipantStatus.JOINED, ParticipantStatus.IN_SESSION, ParticipantStatus.SESSION_COMPLETED]).count()
        if mediation.status in (MediationStatus.CREATED, MediationStatus.INVITED):
            mediation.status = MediationStatus.INVITED
            mediation.save(update_fields=["status"])

        mediation = Mediation.objects.prefetch_related("participants__user").get(pk=mediation.pk)
        AuditLog.record(actor=request.user, action="mediation.joined", mediation=mediation,
                        entity_type="participant", entity_id=participant.pk, request=request)
        for other in mediation.participants.exclude(pk=participant.pk).exclude(status=ParticipantStatus.INVITED).select_related("user"):
            if other.user_id != request.user.id:
                create_notification(
                    other.user,
                    notification_type="join",
                    title="A new participant joined",
                    body=f"{participant.display_name} joined '{mediation.title}'.",
                    mediation=mediation,
                )
        return Response(MediationDetailSerializer(mediation).data)


class MediationStatusView(APIView):
    """GET /api/mediations/{pk}/status/ — live dashboard for participants."""

    def get(self, request, pk):
        mediation = Mediation.objects.filter(pk=pk).prefetch_related(
            "participants__user", "proposals__votes",
        ).first()
        if mediation is None:
            return Response({"detail": "Mediation not found."}, status=status.HTTP_404_NOT_FOUND)
        if not mediation_permission(request, mediation):
            return Response({"detail": "Forbidden."}, status=status.HTTP_403_FORBIDDEN)
        return Response(MediationStatusSerializer(mediation).data)


class MyStatsView(APIView):
    """GET /api/me/stats/ — signed-in user's summary counters."""

    def get(self, request):
        from resolutions.models import Vote

        joined = Participant.objects.filter(user=request.user, status__in=["JOINED", "IN_SESSION", "SESSION_COMPLETED"])
        mediation_ids = set(joined.values_list("mediation_id", flat=True))
        total_votes = Vote.objects.filter(participant__user=request.user).count()
        active_mediations = Mediation.objects.filter(
            id__in=mediation_ids, status__in=["CREATED", "INVITED", "IN_SESSION", "ANALYSIS_READY", "PROPOSALS_READY", "NEGOTIATING"]
        ).count()
        resolved = Mediation.objects.filter(
            id__in=mediation_ids, status__in=["AGREEMENT_SIGNED", "FOLLOW_UP", "REOPENED", "RESOLVED", "CLOSED"]
        ).count()
        return Response({
            "activeMediations": active_mediations,
            "resolvedMediations": resolved,
            "totalMediations": len(mediation_ids),
            "totalVotesCast": total_votes,
            "unreadNotifications": request.user.notifications.filter(is_read=False).count(),
            "mfaEnabled": bool(request.user.is_mfa_enabled),
        })


class MediationParticipantsView(APIView):
    def get(self, request, pk):
        mediation = Mediation.objects.filter(pk=pk).first()
        if mediation is None:
            return Response({"detail": "Mediation not found."}, status=status.HTTP_404_NOT_FOUND)
        if not mediation_permission(request, mediation):
            return Response({"detail": "Forbidden."}, status=status.HTTP_403_FORBIDDEN)
        participants = mediation.participants.select_related("user").order_by("joined_at")
        return Response(ParticipantSerializer(participants, many=True).data)