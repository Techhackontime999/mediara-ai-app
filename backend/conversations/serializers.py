from rest_framework import serializers

from .models import Conversation, Message


class MessageSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    sender = serializers.CharField(read_only=True)
    content = serializers.CharField(read_only=True)
    messageType = serializers.CharField(source="message_type", read_only=True)
    isRiskFlagged = serializers.BooleanField(source="risk_flagged", read_only=True)
    detectedCategory = serializers.SerializerMethodField()
    timestamp = serializers.SerializerMethodField()

    def get_detectedCategory(self, obj):
        return obj.risk_categories[0] if obj.risk_categories else None

    def get_timestamp(self, obj):
        return obj.created_at.isoformat() if obj.created_at else None


class ConversationSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    mediationId = serializers.SerializerMethodField()
    participantId = serializers.SerializerMethodField()
    isPrivate = serializers.BooleanField(source="is_private", read_only=True)
    createdAt = serializers.SerializerMethodField()
    messages = serializers.SerializerMethodField()

    def get_mediationId(self, obj):
        return obj.mediation_id

    def get_participantId(self, obj):
        return obj.participant_id

    def get_createdAt(self, obj):
        return obj.created_at.isoformat() if obj.created_at else None

    def get_messages(self, obj):
        messages = obj.messages.all()
        return MessageSerializer(messages, many=True).data


class SendMessageSerializer(serializers.Serializer):
    message = serializers.CharField(allow_blank=False, trim_whitespace=True)
    participantId = serializers.IntegerField(required=False, allow_null=True,
                                             help_text="Optional; derived from the conversation when omitted.")