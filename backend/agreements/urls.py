from django.urls import path

from .views import (
    AgreementDetailView,
    AgreementHashView,
    AgreementPdfView,
    AgreementVerifyView,
    FinalizeMediationView,
    FollowUpListView,
    FollowUpSubmitView,
    ReopenMediationView,
)

urlpatterns = [
    path("mediations/<int:pk>/finalize/", FinalizeMediationView.as_view(), name="mediation-finalize"),
    path("mediations/<int:pk>/agreement/", AgreementDetailView.as_view(), name="mediation-agreement"),
    path("mediations/<int:pk>/agreement/pdf/", AgreementPdfView.as_view(), name="mediation-agreement-pdf"),
    path("agreements/<int:pk>/pdf/", AgreementPdfView.as_view(), name="agreement-pdf"),
    path("agreements/<int:pk>/hash/", AgreementHashView.as_view(), name="agreement-hash"),
    path("agreements/<int:pk>/verify/", AgreementVerifyView.as_view(), name="agreement-verify"),
    path("mediations/<int:pk>/follow-up/", FollowUpSubmitView.as_view(), name="mediation-follow-up"),
    path("mediations/<int:pk>/follow-ups/", FollowUpListView.as_view(), name="mediation-follow-ups"),
    path("mediations/<int:pk>/reopen/", ReopenMediationView.as_view(), name="mediation-reopen"),
]