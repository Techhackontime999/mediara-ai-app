from django.urls import path

from .views import MediationAuditLogListView, MyAuditLogListView

urlpatterns = [
    path("audit/", MyAuditLogListView.as_view(), name="my-audit"),
    path("mediations/<int:pk>/audit/", MediationAuditLogListView.as_view(), name="mediation-audit"),
]
