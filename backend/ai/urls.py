from django.urls import path

from .grounding_view import GroundingView

urlpatterns = [
    path("mediations/<int:pk>/grounding/", GroundingView.as_view(), name="mediation-grounding"),
]
