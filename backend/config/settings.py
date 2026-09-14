"""
Django settings for Mediara AI.

Configuration is driven by environment variables (see .env.example).
Everything has sensible defaults so the project runs locally with zero
configuration using SQLite + a deterministic local AI engine. PostgreSQL and the
Gemini LLM API are enabled by setting the documented environment variables.
"""

import os
from datetime import timedelta
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent

load_dotenv(BASE_DIR / ".env")


def env_bool(name: str, default: bool = False) -> bool:
    val = os.environ.get(name)
    if val is None:
        return default
    return val.strip().lower() in {"1", "true", "yes", "on"}


def env_list(name: str, default: str = "") -> list[str]:
    raw = os.environ.get(name, default)
    return [item.strip() for item in raw.split(",") if item.strip()]


SECRET_KEY = os.environ.get(
    "DJANGO_SECRET_KEY",
    "django-insecure-dev-key-change-me-in-production-mediara-ai",
)

DEBUG = env_bool("DJANGO_DEBUG", True)

ALLOWED_HOSTS = env_list("DJANGO_ALLOWED_HOSTS", "*")

# Path for the admin backend. Change ADMIN_URL in production to hide /admin/.
ADMIN_URL = os.environ.get("ADMIN_URL", "admin").strip("/")

# Origins that may POST cookies to this origin (reverse proxies, web admin).
CSRF_TRUSTED_ORIGINS = env_list("DJANGO_CSRF_TRUSTED_ORIGINS", "")

# Security hardening. Sensible strict defaults kick in automatically when DEBUG
# is off; each can be overridden per environment. A reverse proxy that handles
# TLS should set DJANGO_SECURE_SSL_REDIRECT=True and forward "X-Forwarded-Proto".
if not DEBUG:
    SESSION_COOKIE_SECURE = env_bool("DJANGO_SESSION_COOKIE_SECURE", True)
    CSRF_COOKIE_SECURE = env_bool("DJANGO_CSRF_COOKIE_SECURE", True)
    SECURE_SSL_REDIRECT = env_bool("DJANGO_SECURE_SSL_REDIRECT", False)
    SECURE_HSTS_SECONDS = int(os.environ.get("DJANGO_HSTS_SECONDS", "31536000"))
    SECURE_HSTS_INCLUDE_SUBDOMAINS = True
    SECURE_HSTS_PRELOAD = True
    SECURE_CONTENT_TYPE_NOSNIFF = True
    SECURE_REFERRER_POLICY = "same-origin"
    SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
    X_FRAME_OPTIONS = os.environ.get("DJANGO_X_FRAME_OPTIONS", "DENY")
else:
    # Local dev: TLS is off, keep cookies usable over HTTP on localhost.
    SESSION_COOKIE_SECURE = False
    CSRF_COOKIE_SECURE = False

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
LOG_LEVEL = os.environ.get("LOG_LEVEL", "INFO").upper()
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "verbose": {"format": "{asctime} {levelname} {name} {message}", "style": "{"},
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "verbose",
        },
    },
    "root": {
        "handlers": ["console"],
        "level": LOG_LEVEL,
    },
    "loggers": {
        "django": {"handlers": ["console"], "level": LOG_LEVEL, "propagate": False},
        "ai": {"handlers": ["console"], "level": LOG_LEVEL, "propagate": False},
        "audit": {"handlers": ["console"], "level": LOG_LEVEL, "propagate": False},
    },
}

# ---------------------------------------------------------------------------
# Applications
# ---------------------------------------------------------------------------
INSTALLED_APPS = [
    "daphne",
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "rest_framework_simplejwt.token_blacklist",
    "drf_spectacular",
    "corsheaders",
    "channels",
    "accounts",
    "mediation",
    "conversations",
    "ai",
    "resolutions",
    "agreements",
    "audit",
    "notifications",
    "web",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.locale.LocaleMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [BASE_DIR / "templates"],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

# ASGI is the primary interface (Daphne / Channels).
ASGI_APPLICATION = "config.asgi.application"
WSGI_APPLICATION = "config.wsgi.application"

# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------
if env_bool("DB_POSTGRES", False) or os.environ.get("POSTGRES_HOST"):
    _pg_options = {}
    sslmode = os.environ.get("POSTGRES_SSLMODE", "").strip()
    if sslmode:
        _pg_options["sslmode"] = sslmode
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.postgresql",
            "NAME": os.environ.get("POSTGRES_DB", "mediara"),
            "USER": os.environ.get("POSTGRES_USER", "mediara"),
            "PASSWORD": os.environ.get("POSTGRES_PASSWORD", "mediara"),
            "HOST": os.environ.get("POSTGRES_HOST", "localhost"),
            "PORT": os.environ.get("POSTGRES_PORT", "5432"),
            "CONN_MAX_AGE": int(os.environ.get("POSTGRES_CONN_MAX_AGE", "0")),
            "OPTIONS": _pg_options,
        }
    }
else:
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.sqlite3",
            "NAME": BASE_DIR / "db.sqlite3",
        }
    }

AUTH_USER_MODEL = "accounts.User"

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator", "OPTIONS": {"min_length": 6}},
]

# ---------------------------------------------------------------------------
# Internationalization
# ---------------------------------------------------------------------------
LANGUAGE_CODE = "en-us"
LANGUAGES = [("en", "English"), ("hi", "Hindi"), ("es", "Spanish"), ("fr", "French")]
LOCALE_PATHS = [BASE_DIR / "locale"]
TIME_ZONE = os.environ.get("DJANGO_TIME_ZONE", "UTC")
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
STATIC_ROOT = os.environ.get("STATIC_ROOT") or (BASE_DIR / "staticfiles")
STATICFILES_DIRS = [BASE_DIR / "static"]

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

MEDIA_ROOT = os.environ.get("MEDIA_ROOT") or (BASE_DIR / "media")
MEDIA_URL = os.environ.get("MEDIA_URL", "/media/")

# ---------------------------------------------------------------------------
# Django REST Framework + JWT
# ---------------------------------------------------------------------------
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
    "DEFAULT_PERMISSION_CLASSES": (
        "rest_framework.permissions.IsAuthenticated",
    ),
    "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.PageNumberPagination",
    "PAGE_SIZE": 50,
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    "DEFAULT_THROTTLE_RATES": {
        "anon": os.environ.get("THROTTLE_ANON", "30/minute"),
        "user": os.environ.get("THROTTLE_USER", "200/minute"),
        "auth": os.environ.get("THROTTLE_AUTH", "10/minute"),
        "login": os.environ.get("THROTTLE_LOGIN", "5/minute"),
        "mfa": os.environ.get("THROTTLE_MFA", "5/minute"),
    },
    "DEFAULT_RENDERER_CLASSES": (
        "rest_framework.renderers.JSONRenderer",
        "rest_framework.renderers.BrowsableAPIRenderer" if DEBUG else "rest_framework.renderers.JSONRenderer",
    ),
}

SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=int(os.environ.get("JWT_ACCESS_MINUTES", "120"))),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=int(os.environ.get("JWT_REFRESH_DAYS", "30"))),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
    "AUTH_HEADER_TYPES": ("Bearer",),
}

# ---------------------------------------------------------------------------
# CORS (needed for a future web client; not required for native Android)
# ---------------------------------------------------------------------------
CORS_ALLOWED_ORIGINS = env_list("CORS_ALLOWED_ORIGINS", "")
CORS_ALLOW_ALL_ORIGINS = env_bool("CORS_ALLOW_ALL_ORIGINS", False)

# ---------------------------------------------------------------------------
# Channels (WebSockets)
# ---------------------------------------------------------------------------
if env_bool("CHANNELS_REDIS", False):
    CHANNEL_LAYERS = {
        "default": {
            "BACKEND": "channels_redis.core.RedisChannelLayer",
            "CONFIG": {"hosts": [os.environ.get("REDIS_URL", "redis://127.0.0.1:6379/0")]},
        }
    }
else:
    # In-memory channel layer (single-process dev). Use Redis in production.
    CHANNEL_LAYERS = {
        "default": {"BACKEND": "channels.layers.InMemoryChannelLayer"},
    }

# ---------------------------------------------------------------------------
# Mediara AI-specific settings
# ---------------------------------------------------------------------------
# LLM provider abstraction -------------------------------------------------
# Pick any OpenAI-compatible endpoint (OpenAI, Groq, Mistral, OpenRouter,
# local Ollama/LM Studio/vLLM…) or keep native Gemini. Only the server holds
# the key; the native Android client never sees it.
#   AI_PROVIDER = "auto" | "gemini" | "openai"       (auto detects from base url)
#   AI_BASE_URL = e.g. https://api.openai.com/v1 , http://localhost:11434/v1
#   AI_API_KEY  = provider key (omit for local Ollama/LM Studio)
#   AI_MODEL    = model id, e.g. gpt-4o-mini, llama3.1, gemini-2.5-flash
AI_PROVIDER = os.environ.get("AI_PROVIDER", "auto")
AI_BASE_URL = os.environ.get("AI_BASE_URL", "")
AI_API_KEY = os.environ.get("AI_API_KEY", "")
AI_MODEL = os.environ.get("AI_MODEL", "")
# Legacy Gemini variables — still honoured (equivalent to AI_PROVIDER=gemini).
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
# When true (default in dev), Gemini failures fall back to the deterministic
# built-in mediation engine so the full flow always works.
AI_FALLBACK_ENABLED = env_bool("AI_FALLBACK_ENABLED", True)

# Where the native client reaches this API (used at runtime by the app config).
PUBLIC_API_BASE_URL = os.environ.get("PUBLIC_API_BASE_URL", "http://10.0.2.2:8000")

# High-risk conflicts are always routed to safety resources, never mediated.
SAFETY_SUPPORT_EMAIL = os.environ.get("SAFETY_SUPPORT_EMAIL", "support@mediara.ai")

# ---------------------------------------------------------------------------
# Email + notifications
# ---------------------------------------------------------------------------
# Default: print to console so verification codes and notification emails work
# with zero configuration. Swap to SMTP_* env vars in production.
EMAIL_BACKEND = "django.core.mail.backends.console.EmailBackend"
if os.environ.get("EMAIL_HOST"):
    EMAIL_BACKEND = "django.core.mail.backends.smtp.EmailBackend"
    EMAIL_HOST = os.environ.get("EMAIL_HOST")
    EMAIL_PORT = int(os.environ.get("EMAIL_PORT", "587"))
    EMAIL_HOST_USER = os.environ.get("EMAIL_HOST_USER", "")
    EMAIL_HOST_PASSWORD = os.environ.get("EMAIL_HOST_PASSWORD", "")
    EMAIL_USE_TLS = env_bool("EMAIL_USE_TLS", True)
    EMAIL_USE_SSL = env_bool("EMAIL_USE_SSL", False)
DEFAULT_FROM_EMAIL = os.environ.get("DEFAULT_FROM_EMAIL", "Mediara AI <no-reply@mediara.ai>")

# Reserved for operational notifications (daily digests, error mailouts).
ADMIN_NOTIFY_EMAILS = env_list("ADMIN_NOTIFY_EMAILS", "")

# ---------------------------------------------------------------------------
# Encryption at rest (EntrustedMessage/Fernet)
# ---------------------------------------------------------------------------
# Key is read by security/crypto.py: ENCRYPTION_KEY env var wins; otherwise we
# persist an auto-generated key to BASE_DIR/entropy.key (secure-file, gitignored).
ENCRYPTION_KEY = os.environ.get("ENCRYPTION_KEY", "")

# ---------------------------------------------------------------------------
# drf-spectacular (OpenAPI docs at /api/schema/ and /api/docs/)
# ---------------------------------------------------------------------------
SPECTACULAR_SETTINGS = {
    "TITLE": "Mediara AI API",
    "DESCRIPTION": "Privacy-first, AI-assisted mediation platform API.",
    "VERSION": "1.0.0",
    "SERVE_INCLUDE_SCHEMA": False,
    "COMPONENT_SPLIT_REQUEST": True,
}

# ---------------------------------------------------------------------------
# Sentry (optional; enabled only when SENTRY_DSN is set)
# ---------------------------------------------------------------------------
SENTRY_DSN = os.environ.get("SENTRY_DSN", "")
APP_ENV = os.environ.get("APP_ENV", "development" if DEBUG else "production")
if SENTRY_DSN:
    import sentry_sdk
    from sentry_sdk.integrations.django import DjangoIntegration

    sentry_sdk.init(
        dsn=SENTRY_DSN,
        integrations=[DjangoIntegration()],
        traces_sample_rate=float(os.environ.get("SENTRY_TRACES_SAMPLE_RATE", "0.1")),
        environment=os.environ.get("SENTRY_ENVIRONMENT", APP_ENV),
    )
