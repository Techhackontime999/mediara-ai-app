from django.contrib import admin

from .models import AuditLog


@admin.register(AuditLog)
class AuditLogAdmin(admin.ModelAdmin):
    list_display = ("created_at", "action", "actor", "entity_type", "entity_id", "mediation_id")
    list_filter = ("action",)
    search_fields = ("action", "actor__email", "entity_id")
    readonly_fields = ("actor", "action", "entity_type", "entity_id", "mediation", "metadata", "ip_address", "created_at")

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False
