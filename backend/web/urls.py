from django.conf import settings
from django.conf.urls.static import static
from django.urls import path

from . import views

urlpatterns = [
    path("", views.landing, name="web-landing"),
    path("login/", views.login_page, name="web-login"),
    path("register/", views.register_page, name="web-register"),
    path("dashboard/", views.dashboard_page, name="web-dashboard"),
    path("mediation/<int:pk>/", views.mediation_detail_page, name="web-mediation-detail"),
    path("account/", views.account_page, name="web-account"),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
