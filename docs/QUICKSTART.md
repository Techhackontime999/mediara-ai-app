# Quickstart

Get the backend running locally in under 5 minutes.

## Prerequisites

- **Python 3.12+** (3.14 confirmed working)
- **Git**
- **Android Studio** (for the Android client — not required for backend work)

## Option A — Bare-metal (fastest)

```bash
# Clone the repo
git clone https://github.com/Techhackontime999/mediara-ai-app.git
cd mediara-ai-app/backend

# Create and activate a virtual environment
python -m venv .venv
# Windows PowerShell:  .venv\Scripts\Activate.ps1
# macOS / Linux:       source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Copy the example env file (all defaults work out of the box)
cp .env.example .env          # Windows:  Copy-Item .env.example .env

# Run migrations and start the dev server
python manage.py migrate
python manage.py runserver     # → http://127.0.0.1:8000/
```

Verify:

```bash
curl http://127.0.0.1:8000/health/       # → {"status":"ok","database":"ok"}
curl http://127.0.0.1:8000/api/docs/     # → Swagger UI
```

### Bootstrap a superuser (optional)

Set these in your `.env` or shell before the first `runserver`:

```bash
DJANGO_SUPERUSER_EMAIL=admin@mediara.ai
DJANGO_SUPERUSER_NAME=Admin
DJANGO_SUPERUSER_PASSWORD=change-me
```

The `ensure_superuser` management command runs automatically in Docker. For bare-metal:

```bash
python manage.py ensure_superuser
```

## Option B — Docker Compose

```bash
cp backend/.env.example backend/.env
# Edit .env: set DJANGO_SECRET_KEY, DJANGO_ALLOWED_HOSTS, POSTGRES_PASSWORD
docker compose up --build     # API on :8000, PostgreSQL, Redis
```

Docker runs `migrate` and `ensure_superuser` on first boot, then starts Daphne.

## Option C — Android emulator → host backend

The Android app ships with `API_BASE_URL` pointing at the production placeholder. To hit a local backend:

```bash
# In the project root (where build.gradle.kts lives):
export API_BASE_URL="http://10.0.2.2:8000/"   # emulator → host loopback
./gradlew installDebug
```

Cleartext HTTP is allowed only for emulator loopback hosts (`10.0.2.2`, `localhost`) via `network_security_config.xml`.

## Next steps

- [CONFIGURATION.md](CONFIGURATION.md) — tune environment variables
- [ANDROID.md](ANDROID.md) — full Android client guide
- [TESTING.md](TESTING.md) — run the test suite
