from rest_framework import serializers


class NotificationSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    type = serializers.CharField(source="notification_type", read_only=True)
    title = serializers.CharField(read_only=True)
    body = serializers.CharField(read_only=True)
    mediationId = serializers.IntegerField(source="mediation_id", read_only=True)
    isRead = serializers.BooleanField(source="is_read", read_only=True)
    createdAt = serializers.SerializerMethodField()

    def get_createdAt(self, obj):
        return obj.created_at.isoformat() if obj.created_at else None
