from django.contrib import admin

from .models import Agreement, FollowUpReport


@admin.register(Agreement)
class AgreementAdmin(admin.ModelAdmin):
    list_display = ("mediation", "resolution", "signed_at", "created_at")


@admin.register(FollowUpReport)
class FollowUpReportAdmin(admin.ModelAdmin):
    list_display = ("mediation", "participant", "sentiment", "reopen_requested", "created_at")
    list_filter = ("sentiment", "reopen_requested")