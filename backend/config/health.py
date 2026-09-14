"""Lightweight liveness + DB health probe (used by Docker healthchecks/CI)."""

from django.conf import settings
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

    # Optional auxiliary MongoDB — reported only when configured.
    mongo_status = "disabled"
    if settings.MONGO_URL:
        try:
            from config.mongo import mongo_available
            mongo_status = "ok" if mongo_available() else "unreachable"
        except Exception:  # noqa: BLE001 - keep the probe non-fatal
            mongo_status = "unknown"

    return JsonResponse({
        "status": "ok" if db_ok else "degraded",
        "database": "ok" if db_ok else "unreachable",
        "mongo": mongo_status,
    }, status=200 if db_ok else 503)
