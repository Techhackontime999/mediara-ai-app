from django.urls import path

from .views import (
    MediationByCodeView,
    MediationDetailView,
    MediationInviteView,
    MediationJoinView,
    MediationListCreateView,
    MediationParticipantsView,
    MediationStatusView,
    MyStatsView,
)

urlpatterns = [
    path("", MediationListCreateView.as_view(), name="mediation-list"),
    path("by-code/<str:code>/", MediationByCodeView.as_view(), name="mediation-by-code"),
    path("join/", MediationJoinView.as_view(), name="mediation-join"),
    path("<int:pk>/", MediationDetailView.as_view(), name="mediation-detail"),
    path("<int:pk>/invite/", MediationInviteView.as_view(), name="mediation-invite"),
    path("<int:pk>/participants/", MediationParticipantsView.as_view(), name="mediation-participants"),
    path("<int:pk>/status/", MediationStatusView.as_view(), name="mediation-status"),
]

me_urlpatterns = [
    path("stats/", MyStatsView.as_view(), name="my-stats"),
]