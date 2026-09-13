from mediation.models import Participant
from mediation.serializers import ParticipantSerializer
from mediation.views import mediation_permission
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Conversation
from .serializers import ConversationSerializer, SendMessageSerializer
from .services import complete_private_session, get_or_create_conversation, send_private_message


class ConversationDetailView(APIView):
    """Return the authenticated user's private conversation for a mediation."""

    def get(self, request, pk):
        from mediation.models import Mediation
        mediation = Mediation.objects.filter(pk=pk).first()
        if mediation is None:
            return Response({"detail": "Mediation not found."}, status=status.HTTP_404_NOT_FOUND)
        if not mediation_permission(request, mediation):
            return Response({"detail": "Forbidden."}, status=status.HTTP_403_FORBIDDEN)
        participant = Participant.objects.filter(mediation=mediation, user=request.user).first()
        if participant is None:
            return Response({"detail": "You are not a participant."}, status=status.HTTP_403_FORBIDDEN)
        conversation = get_or_create_conversation(mediation, participant)
        return Response(ConversationSerializer(conversation).data)


class SendMessageView(APIView):
    """POST /api/conversations/{id}/message/  ->  {user, ai, safetyHold}"""

    def post(self, request, pk):
        conversation = Conversation.objects.filter(pk=pk).select_related("mediation", "participant__user").first()
        if conversation is None:
            return Response({"detail": "Conversation not found."}, status=status.HTTP_404_NOT_FOUND)
        if conversation.participant.user_id != request.user.id:
            return Response({"detail": "Forbidden."}, status=status.HTTP_403_FORBIDDEN)

        serializer = SendMessageSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        content = serializer.validated_data["message"]

        if conversation.mediation.status == "SAFETY_HOLD":
            return Response(
                {"detail": "This mediation is on a safety hold. Mediation is paused."},
                status=status.HTTP_409_CONFLICT,
            )

        result = send_private_message(conversation, content)
        from audit.models import AuditLog
        AuditLog.record(
            actor=request.user,
            action="conversation.message",
            mediation=conversation.mediation,
            entity_type="conversation",
            entity_id=conversation.pk,
            request=request,
            # Metadata only — the private message content is NEVER stored in logs.
            risk_flag="true" if result.get("safetyHold") else "false",
        )
        return Response(result)


class CompleteSessionView(APIView):
    def post(self, request, pk):
        conversation = Conversation.objects.filter(pk=pk).select_related("mediation", "participant").first()
        if conversation is None:
            return Response({"detail": "Conversation not found."}, status=status.HTTP_404_NOT_FOUND)
        if conversation.participant.user_id != request.user.id:
            return Response({"detail": "Forbidden."}, status=status.HTTP_403_FORBIDDEN)
        complete_private_session(conversation)
        from audit.models import AuditLog
        AuditLog.record(
            actor=request.user,
            action="conversation.complete",
            mediation=conversation.mediation,
            entity_type="conversation",
            entity_id=conversation.pk,
            request=request,
        )
        return Response(ParticipantSerializer(conversation.participant).data)


class MyConversationForMediationView(APIView):
    """GET /api/mediations/{id}/my-conversation/"""

    def get(self, request, mediation_id):
        from mediation.models import Mediation
        mediation = Mediation.objects.filter(pk=mediation_id).first()
        if mediation is None:
            return Response({"detail": "Mediation not found."}, status=status.HTTP_404_NOT_FOUND)
        participant = Participant.objects.filter(mediation=mediation, user=request.user).first()
        if participant is None:
            return Response({"detail": "You are not a participant."}, status=status.HTTP_403_FORBIDDEN)
        conversation = get_or_create_conversation(mediation, participant)
        return Response(ConversationSerializer(conversation).data)
