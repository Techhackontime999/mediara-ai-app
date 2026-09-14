# Deployment

This guide covers shipping the backend to production. The Android client build
follows [ANDROID.md](ANDROID.md); the hardened security checklist lives in
[SECURITY.md](../SECURITY.md).

## Production topology

```
Internet
   │  HTTPS (reverse proxy terminates TLS)
   ▼
Reverse proxy (Caddy / Nginx / Cloudflare)   ← DJANGO_SECURE_SSL_REDIRECT=True
   │
   ▼
Django app (Daphne, N workers)  ← Docker container
   ├── PostgreSQL (persistent volume / managed RDS)
   └── Redis (channels layer + follow-up scheduling)
```

## Option A — docker compose (recommended)

1. Copy the env file and fill it in:

   ```bash
   cp backend/.env.example .env
   ```

   Mandatory for prod:

   ```bash
   DJANGO_DEBUG=False
   DJANGO_SECRET_KEY=<64 random bytes>
   DJANGO_ALLOWED_HOSTS=mediara.example.com
   DJANGO_CSRF_TRUSTED_ORIGINS=https://mediara.example.com
   ADMIN_URL=not-the-default-path
   POSTGRES_PASSWORD=<strong>
   ENCRYPTION_KEY=<Fernet key — BACK IT UP>
   EMAIL_HOST=smtp.example.com
   EMAIL_HOST_USER=<user>  EMAIL_HOST_PASSWORD=<app password>
   ```

2. Optional (recommended):

   ```bash
   SENTRY_DSN=https://...   # error tracking
   DJANGO_SUPERUSER_EMAIL/NAME/PASSWORD=...   # auto-create the first admin
   ```

3. Bring it up:

   ```bash
   docker compose up --build -d
   ```

   First boot runs `migrate` → `ensure_superuser` → Daphne. The `api` service
   has a `/health/` healthcheck. `docker compose down` keeps your data in the
   named volumes; **back up the volume with `ENCRYPTION_KEY`** (encrypted
   messages are unrecoverable without it).

## Option B — bare metal

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

export DJANGO_DEBUG=False
export DJANGO_SECRET_KEY=...
export DJANGO_ALLOWED_HOSTS=api.mediara.ai
# ...all importable from .env (see CONFIGURATION.md)

python manage.py migrate
python manage.py ensure_superuser        # bootstrap the admin from env
python manage.py collectstatic --noinput
daphne -b 0.0.0.0 -p 8000 config.asgi:application
```

Run Daphne behind a process supervisor (systemd / Docker restarts) with as many
workers as you need. Use **at least one** in-memory-to-Redis swap:
`CHANNELS_REDIS=True` and `REDIS_URL=redis://redis:6379/0`, required for
multi-worker WebSocket fan-out.

## Reverse proxy essentials

- Terminate TLS; forward `X-Forwarded-Proto: https` and `X-Forwarded-Host`.
- `DJANGO_SECURE_SSL_REDIRECT=True` (or redirect at the proxy).
- Optional: request-rate limiting at the proxy (the app also throttles).

## First superuser

Three ways:

1. **Env bootstrap (recommended)** — set `DJANGO_SUPERUSER_*`; Docker runs it
   for you; bare metal: `python manage.py ensure_superuser`. **Remove or rotate
   `DJANGO_SUPERUSER_PASSWORD` after first boot.**
2. `python manage.py create_superuser` — interactive, on the server.
3. Django admin's own user creation after an initial login.

## Databases & back-ups

- [DATABASE.md](DATABASE.md) covers PostgreSQL provisioning, migration scheme,
  and `pg_dump` backups.
- **Encryption**: with `ENCRYPTION_KEY` set, private messages are Fernet
  encrypted at rest. Backing up the key is non-negotiable.

## Operationalise

- `GET /health/` → `{"status":"ok","database":"ok"}` for load balancers.
- `GET /api/docs/` auto-picked up by Swagger/OpenAPI tooling.
- Sentry alerts for errors; `LOG_LEVEL` tuning lets you dial verbosity.
- Notifications: `python manage.py send_follow_up_reminders` distributes
  14-day check-ins (Docker Compose runs it on boot; schedule it with cron for
  ongoing delivery).

## Update process

```bash
git pull
docker compose pull
docker compose up -d          # runs migrate on boot; data persists

# bare metal
git pull
pip install -r requirements.txt
python manage.py migrate
python manage.py collectstatic --noinput   # only if static assets changed
./restart-daphne.sh
```

## Deploying the Android app

See [ANDROID.md](ANDROID.md): build `assembleRelease` with
`API_BASE_URL=https://api.mediara.ai/`, sign with your keystore, publish via
Play Console (or sideload).