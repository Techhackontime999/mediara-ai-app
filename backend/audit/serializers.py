from rest_framework import serializers


class AuditLogSerializer(serializers.Serializer):
    id = serializers.IntegerField(read_only=True)
    action = serializers.CharField(read_only=True)
    entityType = serializers.CharField(source="entity_type", read_only=True)
    entityId = serializers.CharField(source="entity_id", read_only=True)
    mediationId = serializers.IntegerField(source="mediation_id", read_only=True)
    metadata = serializers.JSONField(read_only=True)
    createdAt = serializers.SerializerMethodField()

    def get_createdAt(self, obj):
        return obj.created_at.isoformat() if obj.created_at else None
