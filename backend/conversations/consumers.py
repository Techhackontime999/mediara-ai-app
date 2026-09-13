"""WebSocket consumer for real-time private AI conversation sessions.

Address:  ws(s)://host/ws/mediations/<mediation_id>/conversations/<conversation_id>/?token=<jwt>

Requires the query parameter "token" = the user's JWT access token (JWT in
browser-storage/WebSocket headers is not possible, so query param is used).
"""

import json

from channels.generic.websocket import AsyncWebsocketConsumer
from rest_framework_simplejwt.tokens import AccessToken

from .services import send_private_message


def _user_from_token(token: str):
    from django.contrib.auth import get_user_model
    User = get_user_model()
    try:
        access = AccessToken(token)
        return User.objects.get(pk=access["user_id"])
    except Exception:
        return None


class ConversationConsumer(AsyncWebsocketConsumer):
    async def connect(self):
        token = self.scope["query_string"].decode().replace("token=", "")
        user = await self._get_user(token)
        if user is None:
            await self.close(code=4001)
            return

        conversation_id = self.scope["url_route"]["kwargs"].get("conversation_id")

        conversation = await self._get_conversation(conversation_id, user)
        if conversation is None:
            await self.close(code=4003)
            return

        self.user = user
        self.conversation = conversation
        self.group_name = f"conversation_{conversation_id}"
        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.accept()
        await self.send(text_data=json.dumps({"type": "connected", "conversationId": conversation_id}))

    async def disconnect(self, close_code):
        if hasattr(self, "group_name"):
            await self.channel_layer.group_discard(self.group_name, self.channel_name)

    async def receive(self, text_data=None, bytes_data=None):
        try:
            payload = json.loads(text_data or "{}")
            message = payload.get("message", "").strip()
        except (json.JSONDecodeError, AttributeError):
            await self.send(text_data=json.dumps({"type": "error", "detail": "Invalid payload."}))
            return
        if not message:
            await self.send(text_data=json.dumps({"type": "error", "detail": "Empty message."}))
            return

        result = await self._process_sync(message)
        await self.send(text_data=json.dumps({"type": "messages", **result}))
        if result.get("safetyHold"):
            await self.send(
                text_data=json.dumps({"type": "safety", "detail": "Mediation paused due to safety concerns."})
            )

    async def _process_sync(self, message):
        from asgiref.sync import sync_to_async
        return await sync_to_async(send_private_message)(self.conversation, message)

    @staticmethod
    async def _get_user(token):
        from asgiref.sync import sync_to_async
        return await sync_to_async(lambda: _user_from_token(token))()

    @staticmethod
    async def _get_conversation(conversation_id, user):
        from asgiref.sync import sync_to_async

        from .models import Conversation

        def fetch():
            return (
                Conversation.objects.select_related("mediation", "participant__user")
                .filter(pk=conversation_id, participant__user=user)
                .first()
            )

        return await sync_to_async(fetch)()
