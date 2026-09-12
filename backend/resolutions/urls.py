from django.urls import path

from .views import (
    AnalysisDetailView,
    AnalyzeView,
    GenerateResolutionsView,
    NegotiationStatusView,
    ResolutionListView,
    ResolutionRefineView,
    ResolutionVoteView,
)

urlpatterns = [
    path("mediations/<int:pk>/analyze/", AnalyzeView.as_view(), name="mediation-analyze"),
    path("mediations/<int:pk>/analysis/", AnalysisDetailView.as_view(), name="mediation-analysis"),
    path("mediations/<int:pk>/generate-resolutions/", GenerateResolutionsView.as_view(), name="mediation-generate-resolutions"),
    path("mediations/<int:pk>/resolutions/", ResolutionListView.as_view(), name="mediation-resolutions"),
    path("mediations/<int:pk>/negotiation/", NegotiationStatusView.as_view(), name="mediation-negotiation"),
    path("resolutions/<int:pk>/vote/", ResolutionVoteView.as_view(), name="resolution-vote"),
    path("resolutions/<int:pk>/refine/", ResolutionRefineView.as_view(), name="resolution-refine"),
]