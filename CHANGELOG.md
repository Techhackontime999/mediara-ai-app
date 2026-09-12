# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

## [1.0.0] - 2026-09-13

### Added

#### Backend — trust, transparency & privacy (Tier 1)
- **Audit trail**: `AuditLog` model + append-only admin; `GET /api/audit/` for your
  own trail and `GET /api/mediations/{id}/audit/` (masked for non-creators so private
  conversation events never leak). Events recorded for register/login/logout, mediation
  create/invite/join, messages, session completion, analysis, votes, refinement,
  finalize, follow-up, reopen, data export/purge, MFA.
- **Auth hardening**: email verification (one-time SHA-256 code, 24h TTL),
  TOTP MFA enrolment + gated login, login/register rate throttling, refresh-token
  reuse detection, logout blacklisting, password-strength validation (8+ with
  mixed case + digit).
- **Encryption at rest**: `security.EncryptedTextField` (Fernet) applied to
  `Message.content`; key from `ENCRYPTION_KEY` or auto-generated dev key.
- **Verified PDF**: deterministic server-generated agreements; SHA-256 fingerprint
  stored at finalize; `GET /api/agreements/{id}/hash/` and
  `POST /api/agreements/{id}/verify/`.
- **GDPR**: `GET /api/auth/data-export/` (full account export) and
  `POST /api/auth/data-purge/` (erase + de-identify).

#### Backend — notifications & live dashboards (Tier 2)
- **Notifications** app: list/read/read-all/unread-count endpoints and
  `create_notification` / `notify_participants` helpers wired into invite, join,
  analysis, vote, refine, finalize, follow-up and reopen events.
- **Follow-up reminder engine**: `python manage.py send_follow_up_reminders --days 14`.
- **Live status**: `GET /api/mediations/{id}/status/` (phase progress, per-participant
  state, proposal votes, next-step guidance) and `GET /api/me/stats/`.
- **Reopen-with-context**: re-opening a mediation snapshots a de-identified prior
  analysis + accepted proposal into `Mediation.reopened_context` so the next AI pass
  knows what failed.

#### Backend — AI quality (Tier 3)
- **Grounding**: proposals carry `grounded_in` citations to the exact participant
  statements behind each benefit/rationale; enforced for LLM and fallback paths and
  re-applied after refinement.
- **Balance / dominance detector**: `GET /api/mediations/{id}/grounding/` returns a
  deterministic conversational-balance report flagging a participant who dominates.
- **Provider abstraction**: single `GEMINI_API_KEY` + deterministic fallback engine
  keeps the whole flow working offline.

#### Backend — engineering maturity (Tier 4)
- OpenAPI docs via drf-spectacular at `/api/schema/` and `/api/docs/`.
- `GET /health/` liveness + DB probe.
- Dockerfile + docker-compose (Daphne ASGI, healthcheck, optional Redis).
- GitHub Actions CI: system check, fresh-DB migrations, full test suite, ruff, mypy.
- Ruff (`pyproject.toml`) and mypy configuration.
- Optional Sentry integration guarded by `SENTRY_DSN`.

#### Android
- Full API-backed client: Compose auth, dashboard, private caucus chat, conflict
  analysis, proposals review + voting/refinement, finalize → agreement → server PDF,
  follow-up check-in and reopen, safety hold surface.
- Auto token refresh, DataStore session persistence, Room cache, wire→domain mappers,
  Robolectric safety/PDF tests.

### Changed
- Auth flow tests updated for the mandatory password-strength policy.
- Alphabetically, nothing else — this is the first tagged feature set.

### Fixed
- Fernet key resolution returned decoded bytes where Fernet expects base64 text.
- Grounding cited-statement dedupe mishandled dictionaries (unhashable list error).
- Agreement PDF referenced an unimported `datetime`.
- Double URL prefix on audit/notifications routes.

### Security
- Private message content is never written to logs or the audit trail.
- Data purge anonymises the account and de-identifies co-participant records.

## [0.1.0] — 2026-08-28 (initial scaffold)

- Backend project with register/login/JWT, mediation create/invite/join, private
  caucus conversation endpoints, deterministic AI pipeline (perspectives → analysis
  → proposals), voting, finalize → accord model, in-app PDF generator.
- Android Compose skeleton with mocked data flows.