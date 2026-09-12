# Security Policy

Mediara AI is a conflict-mediation platform that stores highly sensitive,
confidential user communications. Security is the highest priority of this
project. If you find a vulnerability, please report it responsibly.

## Supported versions

| Version | Supported          |
| ------- | ------------------ |
| main    | :white_check_mark: |

Only the current `main` branch receives security fixes. There are no stable
release branches yet — please plan to deploy against the latest `main`.

## Reporting a vulnerability

**Do not open a public GitHub issue for a security problem.** Please report
privately so we can fix it before disclosure. Use one of these channels:

1. **GitHub private vulnerability reporting** (preferred):
   navigate to the repository → *Security* → *Report a vulnerability*.
2. Drop a message to the repository maintainer through GitHub DMs / email
   listed on the maintainer profile.

### What to include

- The affected endpoint / file and the versions affected.
- A minimal reproduction (what an attacker needs: auth? user interaction?).
- Impact: what an attacker could do (data exposure, privilege escalation, DoS).
- Any suggestion you may have for a fix.

### What happens next

- We acknowledge receipt within **48 hours**.
- We keep you informed of progress; we may ask clarifying questions.
- We aim to ship a fix (and coordinate disclosure) as soon as possible,
  typically within **14 days** for critical issues.
- Once fixed, we will credit you in the release notes unless you prefer to
  remain anonymous.

## Security-relevant areas of the codebase

Please give extra scrutiny to changes touching:

- `backend/accounts/` — authentication, email verification, MFA, token lifecycle.
- `backend/security/` — `EncryptedTextField` and the Fernet key resolution.
- `backend/conversations/` — encryption at rest and the WebSocket endpoints.
- `backend/agreements/` — server-side PDF signing and SHA-256 fingerprinting.
- `backend/audit/` — the audit trail (must never leak message content).
- `backend/ai/` — the safety detector and prompt construction (prompt-injection surface).
- `backend/config/` — settings, CORS, throttling, Sentry wiring.

## Deployment security checklist

Operational hardening required before handling real user data:

- [ ] Set a strong `DJANGO_SECRET_KEY` from a secret manager (never in git).
- [ ] Set `ENCRYPTION_KEY` to a Fernet key and **store a backup** — losing this
      key makes previously encrypted private messages unrecoverable.
- [ ] Set `DJANGO_DEBUG=False` and a restricted `DJANGO_ALLOWED_HOSTS`.
- [ ] Run PostgreSQL (not the SQLite default) and apply backups.
- [ ] Use Redis-backed Channels (`CHANNELS_REDIS=True`) for multi-worker WebSockets.
- [ ] Swap `EMAIL_BACKEND` from console to SMTP for real verification emails.
- [ ] Set `SENTRY_DSN` and monitor 5xx + auth failures.
- [ ] Keep the AI fallback engine available so the service degrades gracefully.
- [ ] Rate-limit aggressively behind a reverse proxy (nginx / cloud load balancer).
- [ ] Every audit-logged request must go through HTTPS in production.

## Known hardening limits (by design)

- WebSockets use the in-memory channel layer in dev; Redis is required in prod.
- Email verification codes print to the server console in dev
  (`/api/auth/dev-resend-code/` is `DEBUG`-only and 403s in production).
- The Android client is API-complete but unbuilt on this machine; the API is the
  security boundary and the app should always talk HTTPS in production.