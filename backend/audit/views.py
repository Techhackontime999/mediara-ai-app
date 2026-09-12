from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import AuditLog
from .serializers import AuditLogSerializer


class MyAuditLogListView(APIView):
    """GET /api/audit/ — the signed-in user's own audit trail (newest first)."""

    def get(self, request):
        queryset = AuditLog.objects.filter(actor=request.user)
        paginator = PageNumberPagination()
        page = paginator.paginate_queryset(queryset, request)
        serializer = AuditLogSerializer(page, many=True)
        return paginator.get_paginated_response(serializer.data)


class MediationAuditLogListView(APIView):
    """GET /api/mediations/{id}/audit/ — audit trail for a mediation, masked.

    Full metadata is only revealed to the mediator/creator; other participants
    get a sanitized event list (action + timestamp + entity) so nobody's private
    input leaks through the audit log.
    """

    def get(self, request, pk):
        from mediation.models import Mediation
        from mediation.views import mediation_permission

        mediation = Mediation.objects.filter(pk=pk).first()
        if mediation is None or not mediation_permission(request, mediation):
            return Response({"detail": "Mediation not found or forbidden."}, status=404)

        events = AuditLog.objects.filter(mediation=mediation)
        is_creator = mediation.creator_id == request.user.pk
        if not is_creator:
            events = events.exclude(action__in=["conversation.message", "conversation.complete"])
        return Response(AuditLogSerializer(events, many=True).data)