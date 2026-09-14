# Configuration

Every backend setting is driven by environment variables read from
`backend/.env` (gitignored) or the container environment. The canonical list
with inline documentation lives in `backend/.env.example`.

## How variables are resolved

1. `backend/config/settings.py` calls `load_dotenv(BASE_DIR / ".env")`.
2. Existing shell/container environment variables **win** over `.env`.
   - Note: an **empty string** in the shell does not beat a non-empty `.env`
     value with this dotenv setup — to override `.env`, set a real value.
3. `python-dotenv` never overrides a variable already set by your shell/Docker.

## Quick reference

### Core security

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `DJANGO_SECRET_KEY` | dev placeholder | **Set in prod.** 64 random bytes (`secrets.token_urlsafe(64)`). |
| `DJANGO_DEBUG` | `True` | `False` in prod. Also enables the dev-only `dev-resend-code` endpoint. |
| `DJANGO_ALLOWED_HOSTS` | `*` | Comma-separated hosts. Restrict in prod. |
| `DJANGO_CSRF_TRUSTED_ORIGINS` | empty | Comma-separated origins allowed to send cookies (browser/2 web). |
| `ADMIN_URL` | `admin` | Path to the Django admin. Obfuscate in prod, e.g. `not-the-admin`. |

### Bootstrapping a superuser

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `DJANGO_SUPERUSER_EMAIL` | empty | Email of the auto-created admin. |
| `DJANGO_SUPERUSER_NAME` | empty | Display name for the admin. |
| `DJANGO_SUPERUSER_PASSWORD` | empty | Admin password. **Remove/rotate after first boot.** |

`python manage.py ensure_superuser` is idempotent: creates when missing,
no-ops when the variables are unset, and re-syncs the password with `--force`.
The Docker entrypoint runs it automatically after `migrate`.

### Security hardening (active when `DJANGO_DEBUG=False`)

| Variable | Default (prod) | Description |
| -------- | -------------- | ----------- |
| `DJANGO_SECURE_SSL_REDIRECT` | `False` | Force HTTPS (set `True` if TLS terminates away from Django). |
| `DJANGO_SESSION_COOKIE_SECURE` | `True` | Session cookie over HTTPS only. |
| `DJANGO_CSRF_COOKIE_SECURE` | `True` | CSRF cookie over HTTPS only. |
| `DJANGO_HSTS_SECONDS` | `31536000` | Strict-Transport-Security duration. |
| `DJANGO_X_FRAME_OPTIONS` | `DENY` | Clickjacking protection. |
| `LOG_LEVEL` | `INFO` | Log level for `django`, `ai`, `audit` loggers. |

### Database

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `DB_POSTGRES` | `False` | Set `True` (or set `POSTGRES_HOST`) to use PostgreSQL; SQLite otherwise. |
| `POSTGRES_DB` / `USER` / `PASSWORD` | `mediara` | PG credentials. |
| `POSTGRES_HOST` / `PORT` | `localhost` / `5432` | PG connection. |
| `POSTGRES_CONN_MAX_AGE` | `0` | Keep-alive seconds (recommend `60` in prod). |
| `POSTGRES_SSLMODE` | empty | e.g. `require` / `verify-full` for managed clouds. |

See [DATABASE.md](DATABASE.md).

### Auth / sessions

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `JWT_ACCESS_MINUTES` | `120` | Access token lifetime. |
| `JWT_REFRESH_DAYS` | `30` | Refresh token lifetime. |
| `THROTTLE_ANON` | `30/minute` | Anonymous API throttle. |
| `THROTTLE_USER` | `200/minute` | Authenticated API throttle. |
| `THROTTLE_AUTH` | `10/minute` | Login/register/MFA endpoint throttle. |
| `THROTTLE_LOGIN` | `5/minute` | Per-user login attempt gate. |
| `THROTTLE_MFA` | `5/minute` | MFA verify gate. |

### AI / LLM provider

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `AI_PROVIDER` | `auto` | `auto` \| `gemini` \| `openai`. `auto` detects from `AI_BASE_URL`. |
| `AI_BASE_URL` | empty | Any OpenAI-compatible base `…/v1` (OpenAI, Groq, OpenRouter, Ollama…). |
| `AI_API_KEY` | empty | Provider key. Omit for local Ollama/LM Studio. |
| `AI_MODEL` | empty | Model id, e.g. `gpt-4o-mini`, `llama3.1`. |
| `GEMINI_API_KEY` | empty | Legacy native Gemini key. |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Legacy native Gemini model. |
| `AI_FALLBACK_ENABLED` | `True` | Fall back to the deterministic engine when the LLM is unreachable. |

See [AI.md](AI.md).

### Mediation / client

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `PUBLIC_API_BASE_URL` | `http://10.0.2.2:8000` | Base URL the Android app uses (10.0.2.2 = emulator loopback). |
| `SAFETY_SUPPORT_EMAIL` | `support@mediara.ai` | Contact shown during a safety hold. |

### Email

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `EMAIL_HOST` | empty | SMTP host; unset → console backend (codes print to stdout). |
| `EMAIL_PORT` | `587` | SMTP port. |
| `EMAIL_HOST_USER` / `PASSWORD` | empty | SMTP credentials (use an app password). |
| `EMAIL_USE_TLS` | `True` | STARTTLS. |
| `EMAIL_USE_SSL` | `False` | Implicit TLS (port 465). |
| `DEFAULT_FROM_EMAIL` | `Mediara AI <no-reply@mediara.ai>` | From header. |

### CORS

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `CORS_ALLOWED_ORIGINS` | empty | Comma-separated browser origins. |
| `CORS_ALLOW_ALL_ORIGINS` | `False` | Allow any origin (dev only). |

Note: the native Android client does not perform CORS; these matter only for a
future browser admin panel.

### Encryption at rest

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `ENCRYPTION_KEY` | auto `backend/entropy.key` | Fernet key for encrypted private messages. **Back it up in prod** — losing it makes messages undecryptable. |

### Observability

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `SENTRY_DSN` | empty | Enable error tracking. |
| `SENTRY_ENVIRONMENT` | `APP_ENV` | Sentry environment label. |
| `SENTRY_TRACES_SAMPLE_RATE` | `0.1` | Trace sampling (0–1). |
| `APP_ENV` | `development`/`production` | Environment label. |

### Files

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `STATIC_ROOT` | `backend/staticfiles` | `collectstatic` output dir. |
| `MEDIA_ROOT` | `backend/media` | Uploaded-file storage (persistent volume in prod). |
| `MEDIA_URL` | `/media/` | Media serving URL prefix. |

## Channels / WebSockets

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `CHANNELS_REDIS` | `False` | Use Redis channel layer (multi-worker prod). |
| `REDIS_URL` | `redis://127.0.0.1:6379/0` | Redis connection string. |