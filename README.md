<div align="center">

# Mediara AI

**Privacy-first, AI-assisted multi-party conflict mediation.**
A production-style platform: a hardened Django REST backend running the full mediation
pipeline (private caucuses → grounded analysis → voting → verifiable agreements →
follow-up), plus a native Android client.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Python](https://img.shields.io/badge/Python-3.12+-blue.svg)](#)
[![Django](https://img.shields.io/badge/Django-5-092E20.svg)](#)
[![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-3DDC84.svg)](#)
[![CI](https://github.com/Techhackontime999/mediara-ai-app/actions/workflows/ci.yml/badge.svg)](https://github.com/Techhackontime999/mediara-ai-app/actions)
[![Docker](https://img.shields.io/badge/docker-ready-2496ED.svg)](#)
[![Code of Conduct](https://img.shields.io/badge/Contributor%20Covenant-2.1-4baaaa.svg)](CODE_OF_CONDUCT.md)

</div>

---

## Table of contents

- [Highlights](#highlights)
- [Architecture](#architecture)
- [Quickstart — backend](#quickstart--backend)
- [Run tests](#run-tests)
- [Run with Docker](#run-with-docker)
- [Android client](#android-client)
- [Two-account testing workflow](#two-account-testing-workflow)
- [API reference](#api-reference)
- [Configuration](#configuration)
- [Trust, transparency & privacy](#trust-transparency--privacy)
- [Project layout](#project-layout)
- [CI / CD](#ci--cd)
- [Deployment to production](#deployment-to-production)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Highlights

- **Full mediation lifecycle end to end**: create → invite-by-code → join → private
  AI caucuses → conflict analysis → 3 grounded proposals → voting & refinement →
  server-signed agreement → official PDF → 14-day efficacy check-ins → reopen.
- **Grounded AI**: every proposal benefit traces back to the participant statements
  that justify it (`grounded_in`), with a deterministic conversational **balance and
  dominance detector** so no one party steers the outcome.
- **Any LLM provider**: OpenAI-compatible `AI_BASE_URL` (OpenAI, Groq, Mistral,
  OpenRouter, local Ollama/LM Studio/vLLM, Hugging Face…) or native Gemini — plus a
  deterministic fallback engine so the flow always works offline.
- **Privacy-first**:
  - Private messages encrypted **at rest** (Fernet) and never logged.
  - De-identified analysis payloads; per-mediation audit events **masked** for
    non-creators.
  - GDPR **data export** and **right to erasure** endpoints.
- **Auth hardening**: email verification, TOTP **MFA**, login throttling,
  refresh-token reuse detection, password strength policy.
- **Trusted documents**: agreement PDFs have a server-recorded **SHA-256
  fingerprint** that can be verified at any time.
- **Operationally ready**: OpenAPI docs, `/health/` probe, Docker + Compose,
  GitHub Actions CI, ruff/mypy, optional Sentry.

## Architecture

```
┌─────────────────────────────┐        ┌─────────────────────────────────────────────┐
│  Android client (Compose)  │  JWT   │              Django backend                 │
│  Retrofit / Moshi / OkHttp │ ─────► │  Daphne (ASGI) · DRF · SimpleJWT · Channels │
│  DataStore · Room cache    │  HTTPS │                                             │
└─────────────────────────────┘        │  accounts ── register·login·MFA·GDPR      │
                                       │  mediation ── cases·invites·participants   │
                                       │  conversations ── private caucuses (E2E    │
                                       │    encrypted at rest, safety-scored)        │
                                       │  ai ── fallback engine ◄─► Gemini (opt.)   │
                                       │  resolutions ── analysis·proposals·votes   │
                                       │  agreements ── finalize·PDF·verification   │
                                       │  audit · notifications · health/schema/docs│
                                       │                                           │
                                       │  Postgres (opt.) · Redis (opt.) · Sentry   │
                                       └─────────────────────────────────────────────┘
```

## Quickstart — backend

Requires **Python 3.12+**.

```bash
cd backend
python -m venv .venv
# Windows:  .venv\Scripts\activate     macOS/Linux:  source .venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py runserver        # → http://127.0.0.1:8000/
```

Verify it is healthy:

```bash
curl http://127.0.0.1:8000/health/          # {"status": "ok", "database": "ok"}
curl http://127.0.0.1:8000/api/docs/        # interactive OpenAPI docs (Swagger)
```

All endpoints live under `/api/` and require a JWT (`Authorization: Bearer <access>`
from `/api/auth/login/`) except `register` and `login`.

### Bring your own model (any OpenAI-compatible API)

Point `AI_BASE_URL` at any service exposing OpenAI-spec `chat/completions` —
no Gemini key required:

```bash
# Local Ollama (no key needed)
export AI_BASE_URL="http://localhost:11434/v1"
export AI_MODEL="llama3.1"

# Groq
export AI_BASE_URL="https://api.groq.com/openai/v1"
export AI_API_KEY="gsk_..."
export AI_MODEL="llama-3.1-8b-instant"

# OpenRouter
export AI_BASE_URL="https://openrouter.ai/api/v1"
export AI_API_KEY="sk-or-..."
export AI_MODEL="meta-llama/llama-3.1-8b-instruct"

# OpenAI
export AI_BASE_URL="https://api.openai.com/v1"
export AI_API_KEY="sk-..."
export AI_MODEL="gpt-4o-mini"
```

Native Gemini still works as before (`GEMINI_API_KEY`), and everything degrades
to the deterministic engine when no provider is configured or reachable.

## Run tests

```bash
cd backend
python manage.py check
python manage.py test                 # 22 tests: full mediation flow, safety, crypto,
                                      # audit, notifications, grounding, balance,
                                      # LLM provider routing
```

## Run with Docker

```bash
docker compose up --build             # API on :8000, optional redis, healthcheck enabled
```

`docker-compose.yml` runs migrations first, then Daphne. WebSockets default to the
in-memory layer; set `CHANNELS_REDIS=true` + `REDIS_URL` for multi-worker production.

## Android client

The app points at the backend via `BuildConfig.API_BASE_URL`.

- Default: `http://10.0.2.2:8000/` (emulator → host loopback).
- Override before building, e.g. for a physical device:

```bash
export API_BASE_URL="http://192.168.1.20:8000/"
./gradlew installDebug
```

The app permits cleartext HTTP for local dev (see `AndroidManifest.xml`); use HTTPS
in production. Build/run in Android Studio (project root) — the client is
API-complete and mirrors every backend feature.

## Two-account testing workflow

1. **Account A** signs up (dev backend: any email + strong password; no SMTP needed) →
   **Create Mediation** → note the invite code.
2. **Account B** signs up → **Join Case** → enter invite code + role.
3. **Account A** opens the case; each participant taps their own **Private Caucus**
   (also verifies your email in dev via the code printed to the server console if
   enabled).
4. Either party runs **Run AI Analysis** → analysis + 3 proposals (grounded citations
   shown on the transparency screen).
5. Both parties vote **Accept / Request Changes (AI Refine) / Reject**.
6. On unanimous accept → **Open Ratified Accord** finalizes server-side (all
   participants auto-signed) → **Official PDF** downloads from the backend +
   `document_hash` recorded.
7. **14-Day Efficacy Check-in** → report sentiment; **Not Working** re-opens the
   mediation keeping prior context.

### Safety hold

If a private-caucus message trips the safety detector, the backend pauses the
mediation (`SAFETY_HOLD` + `409` on new messages) and the app surfaces the Safety
Protection Protocol with crisis resources.

## API reference

### Auth — `/api/auth/`

| Method | Endpoint | Description |
| ------ | -------- | ----------- |
| POST | `register/` | Create account, return `<access>`/`<refresh>` + user |
| POST | `login/` | One-factor login; returns `mfaRequired` if MFA enabled |
| POST | `verify-email/` | Redeem one-time email code (`{"email","code"}`) |
| POST | `dev-resend-code/` | **DEBUG-only**; rotate & return email code |
| POST | `refresh/` | Rotate refresh token (reuse detection) |
| POST | `logout/` | Blacklist refresh token |
| POST | `mfa/start/` | Fresh TOTP secret + `otpauthUri` |
| POST | `mfa/enable/` | Confirm a TOTP code to enable MFA |
| POST | `mfa/verify/` | Complete MFA-gated login with TOTP |
| GET | `me/` | Current user |
| GET | `data-export/` | Full personal data export (GDPR) |
| POST | `data-purge/` | Erase account + de-identify data (GDPR) |

### Mediations — `/api/mediations/`

| Method | Endpoint | Description |
| ------ | -------- | ----------- |
| GET/POST | `` | List / create a mediation |
| GET | `by-code/{code}/` | Look up by invite code (public) |
| POST | `join/` | Join by invite code |
| GET | `{id}/` | Detail: analysis, proposals, agreement, follow-ups, safety |
| POST | `{id}/invite/` | Invite by name/email (creator only) |
| GET | `{id}/participants/` | Participant roster |
| GET | `{id}/status/` | Live dashboard (progress, votes, next step) |
| GET | `/api/me/stats/` | Personal summary counters |

### Private caucuses — `/api/conversations/`

| Method | Endpoint | Description |
| ------ | -------- | ----------- |
| GET | `{id}/` | Your private conversation for a mediation |
| POST | `{id}/message/` | Send a private message → `{user, ai, safetyHold}` |
| POST | `{id}/complete/` | Finish your private session |
| GET | `mediations/{id}/my-conversation/` | Retrieve your conversation for a case |

### Analysis & proposals — `/api/mediations/…`

| Method | Endpoint | Description |
| ------ | -------- | ----------- |
| POST | `mediations/{id}/analyze/` | Full pipeline → analysis + proposals |
| GET | `mediations/{id}/analysis/` | Conflict analysis |
| POST | `mediations/{id}/generate-resolutions/` | Regenerate proposals |
| GET | `mediations/{id}/resolutions/` | Active proposals |
| GET | `mediations/{id}/negotiation/` | Proposals + `approvedByAll` |
| POST | `resolutions/{id}/vote/` | Cast / update a vote |
| POST | `resolutions/{id}/refine/` | Refine a proposal from feedback |

### Agreements — `/api/mediations/{id}/…`

| Method | Endpoint | Description |
| ------ | -------- | ----------- |
| POST | `finalize/` | Finalize + auto-sign all participants |
| GET | `agreement/` | The ratified accord |
| GET | `agreement/pdf/` | Official PDF |
| POST | `follow-up/` | 14-day efficacy check-in |
| GET | `follow-ups/` | Follow-up reports |
| POST | `reopen/` | Re-open a resolved/agreement case |

`/api/agreements/{id}/`: `pdf/`, `hash/`, `verify/` (POST `{"sha256":"…"}`).

### Auditing, notifications & AI transparency

| Method | Endpoint | Description |
| ------ | -------- | ----------- |
| GET | `/api/audit/` | Your audit trail (paginated) |
| GET | `/api/mediations/{id}/audit/` | Case audit (masked for non-creators) |
| GET | `/api/notifications/` | Your notifications |
| POST | `/api/notifications/{id}/read/` | Mark one read |
| POST | `/api/notifications/read-all/` | Mark all read |
| GET | `/api/notifications/unread-count/` | Unread count |
| GET | `/api/mediations/{id}/grounding/` | Proposal citations + balance report |

### Meta

`/health/` · `/api/schema/` (OpenAPI JSON) · `/api/docs/` (Swagger UI) · `/admin/`

## Configuration

All settings are driven by environment variables — see
[`backend/.env.example`](backend/.env.example). Key ones:

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `DJANGO_SECRET_KEY` | dev key | Django secret — set in prod |
| `DJANGO_DEBUG` | `True` | Disable in prod |
| `DJANGO_ALLOWED_HOSTS` | `*` | Restrict in prod |
| `DB_POSTGRES` | `False` | Use PostgreSQL when `True` |
| `AI_PROVIDER` | `auto` | `auto` \| `gemini` \| `openai` |
| `AI_BASE_URL` | empty | Any OpenAI-compatible base URL (e.g. Ollama `http://localhost:11434/v1`) |
| `AI_API_KEY` | empty | Provider key (omit for local Ollama/LM Studio) |
| `AI_MODEL` | empty | Model id, e.g. `gpt-4o-mini`, `llama3.1`, `gemini-2.5-flash` |
| `GEMINI_API_KEY` / `GEMINI_MODEL` | empty / `gemini-2.5-flash` | Legacy Gemini (equivalent to `AI_PROVIDER=gemini`) |
| `ENCRYPTION_KEY` | auto file | Fernet key for at-rest encryption |
| `JWT_ACCESS_MINUTES` / `JWT_REFRESH_DAYS` | `120` / `30` | Token lifetimes |
| `THROTTLE_AUTH` | `10/minute` | Login/register rate limit |
| `EMAIL_HOST`… | console | SMTP for verification emails (console in dev) |
| `SENTRY_DSN` | empty | Error tracking (enabled when set) |
| `PUBLIC_API_BASE_URL` | `http://10.0.2.2:8000` | Base URL the Android app uses |

## Trust, transparency & privacy

- **Encryption at rest** — private caucus messages are Fernet-encrypted
  (`security.fields.EncryptedTextField`); `ENCRYPTION_KEY` in prod, auto-generated
  `backend/entropy.key` (gitignored) in dev.
- **Verified PDFs** — SHA-256 fingerprint recorded server-side at finalize; verify
  any copy via `hash/` + `verify/`.
- **Grounded & balanced AI** — citations attach every benefit/proposal rationale to
  the actual participant statements; the balance detector flags a dominating party.
- **Audit trail without leaks** — full event trail, but private message *content* is
  never recorded; conversation events are hidden from non-creators.
- **GDPR** — data export and erasure endpoints built in.

## Project layout

```
backend/
  accounts         auth, email-verify, MFA, GDPR export/erase
  mediation        cases, invite codes, participants, status dashboard
  conversations    private caucuses (encrypted at rest), WebSocket consumers
  ai               safety, grounding, balance, LLM fallback engine
  resolutions      conflict analysis, proposals, votes, refinement
  agreements       finalize, accord, verifiable PDF, follow-up, reopen
  audit            append-only audit trail API
  notifications    in-app notifications + reminder command
  security         Fernet crypto + EncryptedTextField
  config           settings, health probe, root URLs, schema/docs
app/
  data/remote      DTOs, MediaraApiService, ApiClient, SessionManager
  data/repository  MediationRepository (single API facade)
  data/MediaraMappers.kt   wire → domain mapping
  ui               Compose screens (auth, dashboard, caucus, proposals, agreement)
.github/workflows ci.yml     GitHub Actions CI
Dockerfile · docker-compose.yml   containerised API (+ optional redis)
pyproject.toml        ruff + mypy config
```

## CI / CD

`.github/workflows/ci.yml` runs on every push/PR to `main`:

1. `python manage.py check`
2. Fresh-DB `migrate`
3. Full test suite (`manage.py test`)
4. `ruff check .`
5. `mypy` (informational, non-blocking)

Docker image is buildable from the repo root (`docker compose up --build`).

## Deployment to production

See **[SECURITY.md](SECURITY.md)** for the full hardened deployment checklist.
Minimum for real user data (non-negotiable):

- `DJANGO_DEBUG=False`, strong secret key, restricted hosts, HTTPS everywhere.
- PostgreSQL instead of SQLite, with backups.
- `ENCRYPTION_KEY` set **and backed up** (unrecoverable data otherwise).
- Real SMTP for verification emails.
- Redis-backed Channels for multi-worker WebSockets (`CHANNELS_REDIS=true`).
- Optional but recommended: Sentry DSN + rate limiting at the reverse proxy.

## Roadmap

- **v1.1** — Hilt / MVVM refactor of the Android client; pull-to-refresh live dashboards.
- **v1.2** — Structured mediation templates per category (rental, workplace, family).
- **v1.3** — Web portal (reuses the existing DRF API + OpenAPI schema).
- **v1.4** — Celery-based scheduled reminders + aggregated outcomes analytics
  (de-identified, opt-in).
- **v1.5** — Anthropic-native protocol adapter behind the existing provider
  abstraction (OpenAI-compatible + Gemini already supported).

*Deferred by design*: raw WebSocket fan-out requires Redis (scaffolded, in-memory in
dev); SMTP/Gemini/MFA production provisioning is operator-owned; the Android client
builds in Android Studio (no SDK on this machine).

## Contributing

We welcome contributions of all kinds. Please read
**[CONTRIBUTING.md](CONTRIBUTING.md)** first — it covers conventions, the dev
workflow, commit-message style, and the privacy rules every contributor must follow.

- **Bug reports / feature requests** → use the issue templates.
- **Security issues** → report **privately** via the repository's Security tab
  (see [SECURITY.md](SECURITY.md)); do not open a public issue.
- All community interaction is governed by the
  [Contributor Covenant](CODE_OF_CONDUCT.md).

## License

Released under the [MIT License](LICENSE). Copyright © 2026 Mediara AI contributors.