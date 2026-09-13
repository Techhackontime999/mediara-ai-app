"""Lightweight liveness + DB health probe (used by Docker healthchecks/CI)."""

from django.db import connection
from django.http import JsonResponse


def health(request):
    db_ok = True
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
    except Exception:  # noqa: BLE001 - report any DB failure as unhealthy
        db_ok = False
    return JsonResponse({
        "status": "ok" if db_ok else "degraded",
        "database": "ok" if db_ok else "unreachable",
    }, status=200 if db_ok else 503)
