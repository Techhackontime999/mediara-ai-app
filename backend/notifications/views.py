from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Notification
from .serializers import NotificationSerializer


class NotificationListView(APIView):
    """GET /api/notifications/ — the signed-in user's notifications."""

    def get(self, request):
        queryset = Notification.objects.filter(user=request.user).select_related("mediation")[:100]
        return Response(NotificationSerializer(queryset, many=True).data)


class NotificationUnreadCountView(APIView):
    def get(self, request):
        return Response({"unread": Notification.objects.filter(user=request.user, is_read=False).count()})


class NotificationReadView(APIView):
    """POST /api/notifications/{id}/read/"""

    def post(self, request, pk):
        notification = Notification.objects.filter(pk=pk, user=request.user).first()
        if notification is None:
            return Response({"detail": "Notification not found."}, status=404)
        notification.is_read = True
        notification.save(update_fields=["is_read"])
        return Response(NotificationSerializer(notification).data)


class NotificationReadAllView(APIView):
    def post(self, request):
        count = Notification.objects.filter(user=request.user, is_read=False).update(is_read=True)
        return Response({"updated": count})