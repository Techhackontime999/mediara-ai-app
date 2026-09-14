"""Root URL configuration for the Mediara AI API."""

from django.conf import settings
from django.contrib import admin
from django.urls import include, path
from drf_spectacular.views import SpectacularAPIView, SpectacularSwaggerView
from mediation.urls import me_urlpatterns

from .health import health

urlpatterns = [
    path("health/", health, name="health"),
    path(f"{settings.ADMIN_URL}/", admin.site.urls),
    path("api/auth/", include("accounts.urls")),
    path("api/mediations/", include("mediation.urls")),
    path("api/conversations/", include("conversations.urls")),
    path("api/", include("resolutions.urls")),
    path("api/", include("agreements.urls")),
    path("api/", include("audit.urls")),
    path("api/notifications/", include("notifications.urls")),
    path("api/me/", include(me_urlpatterns)),
    path("api/", include("ai.urls")),
    path("api/schema/", SpectacularAPIView.as_view(), name="schema"),
    path("api/docs/", SpectacularSwaggerView.as_view(url_name="schema"), name="docs"),
    path("", include("web.urls")),
]
