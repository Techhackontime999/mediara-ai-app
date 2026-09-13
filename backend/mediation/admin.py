from django.contrib import admin

from .models import Mediation, Participant


class ParticipantInline(admin.TabularInline):
    model = Participant
    extra = 0
    readonly_fields = ("status", "joined_at")


@admin.register(Mediation)
class MediationAdmin(admin.ModelAdmin):
    list_display = ("title", "category", "status", "invite_code", "creator", "created_at")
    list_filter = ("status", "category", "safety_hold")
    search_fields = ("title", "invite_code", "creator__email")
    inlines = [ParticipantInline]


@admin.register(Participant)
class ParticipantAdmin(admin.ModelAdmin):
    list_display = ("mediation", "display_name", "email", "role", "status")
    list_filter = ("status",)
    search_fields = ("name", "email", "user__email", "mediation__title")
