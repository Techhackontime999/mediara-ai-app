from django.urls import path

from .consumers import ConversationConsumer

websocket_urlpatterns = [
    path("ws/mediations/<int:mediation_id>/conversations/<int:conversation_id>/", ConversationConsumer.as_asgi()),
]
