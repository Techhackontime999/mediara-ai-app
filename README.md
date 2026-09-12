# Mediara AI

A production-style multi-party conflict mediation platform: a real Django REST backend
(running the full AI mediation pipeline) plus a native Android client.

- **Backend** (`backend/`): Django + DRF + SimpleJWT. Register/login (email verification + MFA),
  mediations with invite codes, confidential private caucuses, AI conflict analysis, 3 resolution
  proposals that are **grounded** in participant statements (with a live balance/dominance check),
  voting & refinement, finalize → official agreement → server-generated **SHA-256 verifiable PDF**,
  14-day follow-up check-ins (re-open keeps prior context), **encrypted-at-rest** private messages,
  **full audit trail**, **notifications** (+ reminder engine), **GDPR export & erasure**, in-app
  follow-up reminder command, safety hold & crisis resources.
- **Android app** (`app/`): Jetpack Compose client backed by Retrofit + Moshi + OkHttp,
  with automatic token refresh, DataStore session persistence, and a Room cache.
- **Operations** (`Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml`, `pyproject.toml`):
  containerised API (Daphne + Channels + Redis-ready), `/health/` probe, OpenAPI docs at `/api/docs/`,
  GitHub Actions CI that runs checks + migrations + the full test suite.

## Prerequisites

- Python 3.12+ (venv recommended)
- Android Studio with an emulator (or a physical device on the same network)

## 1. Run the backend

```bash
cd backend
python -m venv .venv          # or reuse an existing venv
# Windows:
.venv\Scripts\activate
pip install -r requirements.txt
python manage.py migrate
python manage.py runserver    # serves on http://127.0.0.1:8000/
```

Django CORS is configured for `http://10.0.2.2` (emulator) and loopback. All API
endpoints are under `/api/` and require a JWT (`Bearer <access>` from
`/api/auth/login/`) except `register` and `login`.

### Backend tests

```bash
cd backend
../.venv/Scripts/python manage.py test   # Windows
# or: python manage.py test
```

### Trust, transparency & privacy features

- **Encryption at rest**: private caucus messages are Fernet-encrypted (`security.fields.EncryptedTextField`).
  Set `ENCRYPTION_KEY` (Fernet key) in production; in dev an auto-generated `backend/entropy.key`
  is used and gitignored.
- **Verified PDF**: every agreement PDF is fingerprinted with SHA-256 at finalise time.
  Verify any copy via `GET /api/agreements/{id}/hash/` and `POST /api/agreements/{id}/verify/`
  (`{"sha256": "..."}`).
- **Grounding & balance**: `GET /api/mediations/{id}/grounding/` shows which participant statements
  each proposal benefit traces back to, plus a conversational-balance report.
- **Audit trail**: `GET /api/audit/` (own) and `GET /api/mediations/{id}/audit/` (masked for
  non-creators).
- **Notifications**: `GET /api/notifications/`, `POST /api/notifications/{id}/read/`,
  `POST /api/notifications/read-all/`, `GET /api/notifications/unread-count/`. Reminder engine:
  `python manage.py send_follow_up_reminders --days 14`.
- **Privacy (GDPR)**: `GET /api/auth/data-export/` and `POST /api/auth/data-purge/`.
- **Auth hardening**: email verification (`/api/auth/email-verify/`, dev code via
  `/api/auth/dev-resend-code/`), MFA (TOTP: `/api/auth/mfa/start|enable|verify/`), login throttling,
  refresh-token reuse detection, password-strength validation.
- **Dashboard**: `GET /api/mediations/{id}/status/` (progress + votes + next step) and
  `GET /api/me/stats/`.

### Tier status

| Tier | Scope | Status |
| ---- | ----- | ------ |
| 1 | Audit trail, auth hardening, encryption-at-rest, verified PDF, GDPR | Done in backend |
| 2 | Notifications + follow-up reminders, live status/dashboard, reopen-with-context | Done in backend |
| 3 | LLM provider abstraction + deterministic fallback, grounding, balance/bias detector | Done in backend |
| 4 | OpenAPI docs, Docker, GitHub Actions CI, ruff/mypy, Sentry, health probe | Done in backend |

Deferred (documented, not implemented here): WebSockets need Redis in production (scaffolded, in-memory
layer in dev), real SMTP/Gemini keys/MFA are not provisioned (email prints to console in dev, AI falls
back to the deterministic engine), and the Android client is API-complete but not buildable on this
machine (no Android SDK), so it should be compiled in Android Studio.

## 2. Point the app at the backend

The app's base URL is baked in at build time via `BuildConfig.API_BASE_URL`.

- Default: `http://10.0.2.2:8000/` (Android Studio emulator → host loopback).
- Override: set the `API_BASE_URL` environment variable before building, e.g. for a
  physical device on your LAN:

```bash
export API_BASE_URL="http://192.168.1.20:8000/"
./gradlew installDebug
```

The app permits cleartext HTTP for local development (see `AndroidManifest.xml`).

## 3. Run the app

1. Open the project root in Android Studio.
2. Build/run on an emulator (or device reachable at `API_BASE_URL`).
3. Create an account on the Sign-up screen (any email + password; dev backend has no
   email verification).

### Two-account testing workflow

A mediation needs at least two parties. To exercise the full lifecycle end to end:

1. **Account A** signs up, then **Create Mediation**. Note the invite code shown in the
   detail screen.
2. **Account B** signs up (regular sign-up on the same app/emulator), then **Join Case**
   and enters the invite code + their role.
3. **Account A** opens the case → both participants run their **Private Caucus** (the
   creator can start theirs; each participant's own session button is enabled only for
   their account).
4. Either party taps **Run AI Analysis** → 3 proposals appear.
5. Both parties cast votes (Accept / Request Changes → AI Refine / Reject) on the
   proposals tab.
6. When everyone accepts one proposal, **Open Ratified Accord** finalizes the mediation
   server-side (all participants are auto-signed) → review the accord → **Generate &
   Open Official PDF** (downloaded from the backend).
7. **14-Day Efficacy Check-in** records a follow-up; **Not Working** re-opens the
   mediation.

### Safety hold

If a private-caucus message trips the safety detector, the backend pauses the mediation
(`SAFETY_HOLD`) and the app surfaces the Safety Protection Protocol with crisis hotlines.

## Project layout

- `backend/accounts` — auth (register/login/refresh/logout/me)
- `backend/mediation` — mediations, participants, invite/join, conversations
- `backend/resolutions` — analysis, proposals, votes, refinement
- `backend/agreements` — finalize, accord, PDF, follow-up, reopen
- `backend/ai` — deterministic LLM pipeline (perspectives → analysis → 3 proposals)
- `backend/config` — settings, URLs, CORS
- `app/.../data/remote` — DTOs, `MediaraApiService`, `ApiClient`, `SessionManager`
- `app/.../data/repository` — `MediationRepository` (single API-facing facade)
- `app/.../data/MediaraMappers.kt` — wire → domain mapping
- `app/.../ui` — Compose screens