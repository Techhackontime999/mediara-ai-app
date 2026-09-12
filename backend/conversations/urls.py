from django.urls import path

from .views import (
    CompleteSessionView,
    ConversationDetailView,
    MyConversationForMediationView,
    SendMessageView,
)

urlpatterns = [
    path("<int:pk>/", ConversationDetailView.as_view(), name="conversation-detail"),
    path("<int:pk>/message/", SendMessageView.as_view(), name="conversation-message"),
    path("<int:pk>/complete/", CompleteSessionView.as_view(), name="conversation-complete"),
    path("mediations/<int:mediation_id>/my-conversation/", MyConversationForMediationView.as_view(), name="my-conversation"),
]