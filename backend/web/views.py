"""Server-rendered pages for the Mediara web client.

The web client is an HTML/JS shell that reuses the existing DRF JSON API. These
views only render the static page structure; all data operations happen in the
browser against the /api/ endpoints with the JWT kept client-side.
"""

from django.conf import settings
from django.shortcuts import render


def _context(request):
    return {
        "debug": settings.DEBUG,
        "app_name": "Mediara AI",
        "api_base": "/api/v",
    }


def landing(request):
    return render(request, "web/landing.html", _context(request))


def login_page(request):
    return render(request, "web/login.html", _context(request))


def register_page(request):
    return render(request, "web/register.html", _context(request))


def dashboard_page(request):
    return render(request, "web/dashboard.html", _context(request))


def mediation_detail_page(request, pk):
    return render(request, "web/mediation_detail.html", {**_context(request), "mediation_id": pk})


def account_page(request):
    return render(request, "web/account.html", _context(request))
