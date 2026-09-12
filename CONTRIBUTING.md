# Contributing to Mediara AI

First off — thank you for considering a contribution. We want contributing to be
easy, safe, and rewarding.

## Table of contents

1. [Code of conduct](#code-of-conduct)
2. [What we're looking for](#what-were-looking-for)
3. [Getting started](#getting-started)
4. [Project conventions](#project-conventions)
5. [Development workflows](#development-workflows)
6. [Submitting changes](#submitting-changes)
7. [Issue reporting guidelines](#issue-reporting-guidelines)
8. [Commit message style](#commit-message-style)

## Code of conduct

This project and everyone participating in it is governed by our
[CODE_OF_CONDUCT](CODE_OF_CONDUCT.md). By participating, you are expected to
uphold this code. Please report unacceptable behaviour to the maintainers.

## What we're looking for

We welcome all kinds of contributions:

- **Bug fixes and security fixes** — the highest priority.
- **New API endpoints or backend features** that fit the mediation lifecycle.
- **Android UI/UX improvements** and fixes to the Compose client.
- **Tests** — increasing coverage of the mediation flow and the AI fallback engine.
- **Documentation** — README, API guides, in-code docstrings, deployment recipes.
- **Design reviews** — proposals for accessibility, safety, or privacy improvements.

Contributions that **do not** fit:

- Breaking API changes without a migration path.
- Additions that store more sensitive data than the current design already stores.
- Copy-paste "features" with no tests or rationale.

## Getting started

### 1. Fork & clone

```bash
git clone https://github.com/Techhackontime999/mediara-ai-app.git
cd mediara-ai-app
git checkout -b your-feature-branch
```

### 2. Set up the backend

```bash
cd backend
python -m venv .venv
# Windows: .venv\Scripts\activate   |   macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py runserver
```

You should be able to run the full test suite before you touch anything:

```bash
python manage.py check
python manage.py test
```

### 3. Optional extras

- Copy `backend/.env.example` to `backend/.env` and tweak values.
- Set `GEMINI_API_KEY` to exercise the LLM path (otherwise the deterministic
  fallback engine is used, which is fully testable offline).
- `ENCRYPTION_KEY` for encryption at rest (a dev key is auto-generated if unset).

## Project conventions

### Python / Django backend

- **Python 3.12+**, black-style formatting, max line length ~120 (see `pyproject.toml`).
- Public API JSON uses **camelCase** field names; Python attributes stay snake_case.
- IDs are integers, timestamps are ISO-8601 strings.
- Private caucus message content must **never** appear in logs or the audit trail —
  only metadata. Treat `Message.content` as encrypted-at-rest and confidential.
- Data de-identification: never serialise raw private caucus text into analysis
  payloads that get persisted. `Perspective` rows are already de-identified.
- New views follow the existing `APIView` + `Serializer` pattern (no generic
  `ModelViewSet` endpoints unless discussed).

### Android (Kotlin / Compose)

- `data/remote` (DTOs), `data/repository` (facade), `ui` (Compose screens).
- Map server integer IDs to `Long` and ISO timestamps to epoch millis in the mapper layer.
- Compose screens use `remember { mutableStateOf(...) }` + LaunchedEffect; no ViewModel
  dependency currently — propose one in a PR before introducing it.

### Commits

Follow [Conventional Commits](https://www.conventionalcommits.org/) — see
[Commit message style](#commit-message-style).

## Development workflows

### Running the tests

```bash
cd backend
python manage.py test                  # full suite
python manage.py test resolutions     # single app
python manage.py test resolutions.tests.FullMvpFlowTests.test_full_mvp_flow  # single test
```

The full suite runs in CI on every push (`.github/workflows/ci.yml`) — make sure
it is green **before** requesting review.

### Linting & type checks

```bash
pip install ruff mypy
ruff check .
mypy config accounts mediation conversations ai resolutions agreements audit notifications security --ignore-missing-imports
```

Ruff config lives in `pyproject.toml`.

### The full mediation lifecycle (smoke test)

The test suite includes `FullMvpFlowTests` that drives the whole flow through the
HTTP API — register → create → join → caucus → analyze → vote → finalize → PDF →
follow-up. If it passes, the core is healthy.

### Exporting the OpenAPI schema

```bash
python manage.py spectacular --file schema.json
```

## Submitting changes

1. Make your change on a branch off `main`.
2. Add/update tests for the change.
3. Run `python manage.py check` and `python manage.py test`.
4. For backend changes, run `ruff check .`; fix all issues.
5. Commit with a clear, conventional message (see below).
6. Open a PR against `main` using the [PR template](.github/PULL_REQUEST_TEMPLATE.md).
7. Wait for CI. Address any failures.

Small, focused PRs are much easier to review than large ones. If a change is
large, open an issue first to discuss the design.

### Commit message style

```
<type>(<scope>): <subject>

<optional body>
```

- `type`: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `security`, `ci`
- `scope`: e.g. `auth`, `agreements`, `mediation`, `ai`, `notifications`, `android`, `docs`

Examples:

```
feat(auth): add TOTP MFA enrollment and gated login

fix(agreements): verify agreement PDF hash against server fingerprint

docs(readme): document deployment options for production
```

## Issue reporting guidelines

- **Search first** — check existing issues before opening a duplicate.
- **Bug reports** — use the [Bug report](.github/ISSUE_TEMPLATE/bug_report.yml) template.
  Include: environment (OS, Python version, backend or Android), steps to reproduce,
  expected vs. actual behaviour, and logs if available.
- **Feature requests** — use the [Feature request](.github/ISSUE_TEMPLATE/feature_request.yml)
  template and describe the problem you are solving, not just the feature name.
- **Security issues** — do **not** open a public issue. Report via the process in
  [SECURITY.md](SECURITY.md).

---

Questions before starting? Open a discussion or ask on your PR. Thank you for
helping make calm, fair, AI-assisted mediation a reality.