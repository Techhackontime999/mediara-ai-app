"""Service functions for private AI conversations."""

from ai import services as ai_services
from mediation.models import ParticipantStatus

from .models import Conversation, Message, SenderType


def get_or_create_conversation(mediation, participant) -> Conversation:
    conversation, _ = Conversation.objects.get_or_create(
        mediation=mediation,
        participant=participant,
        defaults={"is_private": True},
    )
    return conversation


def send_private_message(conversation: Conversation, content: str) -> dict:
    """Create the user message, generate the AI mediator reply, persist both.

    Returns {"user": <message dict>, "ai": <message dict>, "safetyHold": bool}
    """
    mediation = conversation.mediation
    participant = conversation.participant
    history = list(conversation.messages.all())

    from ai.safety import evaluate_content
    safety = evaluate_content(content)

    user_message = Message.objects.create(
        conversation=conversation,
        sender=SenderType.USER,
        content=content.strip(),
        risk_flagged=safety.is_high_risk,
        risk_categories=safety.detected_risks if safety.is_high_risk else [],
    )

    reply_text, category = ai_services.private_session_reply(mediation, participant, history, content)
    ai_message = Message.objects.create(
        conversation=conversation,
        sender=SenderType.AI_MEDIATOR,
        content=reply_text,
        message_type="SAFETY_ALERT" if category == "SAFETY_ALERT" else "TEXT",
        risk_flagged=safety.is_high_risk,
        risk_categories=safety.detected_risks if safety.is_high_risk else [],
    )

    safety_hold = safety.is_high_risk
    if safety_hold:
        mediation.safety_hold = True
        mediation.status = "SAFETY_HOLD"
        mediation.save(update_fields=["safety_hold", "status"])

    if participant.status == ParticipantStatus.JOINED:
        participant.status = ParticipantStatus.IN_SESSION
        participant.save(update_fields=["status"])

    return {
        "user": _to_dict(user_message),
        "ai": _to_dict(ai_message),
        "safetyHold": safety_hold,
    }


def complete_private_session(conversation: Conversation) -> None:
    participant = conversation.participant
    participant.status = ParticipantStatus.SESSION_COMPLETED
    participant.has_completed_private_session = True
    participant.save(update_fields=["status", "has_completed_private_session"])

    mediation = conversation.mediation
    required = mediation.participants.exclude(status="INVITED")
    if required.exists() and required.exclude(status=ParticipantStatus.SESSION_COMPLETED).count() == 0 and mediation.status in ("CREATED", "INVITED"):
        mediation.status = "IN_PROGRESS"
        mediation.save(update_fields=["status"])


def _to_dict(message: Message) -> dict:
    return {
        "id": message.id,
        "sender": message.sender,
        "content": message.content,
        "messageType": message.message_type,
        "isRiskFlagged": message.risk_flagged,
        "detectedCategory": message.risk_categories[0] if message.risk_categories else None,
        "timestamp": message.created_at.isoformat() if message.created_at else None,
    }
