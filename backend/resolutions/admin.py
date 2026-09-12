from django.contrib import admin

from .models import Perspective, ConflictAnalysis, Resolution, Vote


class VoteInline(admin.TabularInline):
    model = Vote
    extra = 0
    readonly_fields = ("participant", "decision", "feedback", "created_at")


@admin.register(Perspective)
class PerspectiveAdmin(admin.ModelAdmin):
    list_display = ("mediation", "participant", "created_at")


@admin.register(ConflictAnalysis)
class ConflictAnalysisAdmin(admin.ModelAdmin):
    list_display = ("mediation", "compatibility_score", "created_at")


@admin.register(Resolution)
class ResolutionAdmin(admin.ModelAdmin):
    list_display = ("title", "mediation", "proposal_number", "refinement_iteration", "is_active")
    list_filter = ("is_active",)
    inlines = [VoteInline]