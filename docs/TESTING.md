# Testing

## Backend

```bash
cd backend
python manage.py check             # config sanity
python manage.py test              # full suite (22 tests)
python manage.py test accounts     # one app
python manage.py test mediation.tests.MediationFlowTests -v 2   # one class
```

### What the suite covers

- **Auth**: register → JWT, duplicate rejection, email-verification gate, login
  success/failure, `/me/` auth requirement.
- **Full mediation flow**: create → invite → join → private caucus messages
  (incl. safety hold) → analysis → proposals → votes → finalize → PDF hash →
  follow-up.
- **Crypto**: Fernet at-rest encryption round-trip.
- **AI**: provider routing (`auto`/`gemini`/`openai`, none), grounded citations,
  balance & dominance detector.
- **Audit + notifications**.

### Offline / deterministic mode

If your `.env` holds a real LLM key, tests that exercise the mediation pipeline
wait on real provider calls (60s timeouts). To force the deterministic engine:

```bash
export AI_BASE_URL="http://127.0.0.1:59999/v1"   # connection refused → instant fallback
python manage.py test
```

### Lint & types

CI runs ruff and mypy (`pyproject.toml` at the root):

```bash
python -m ruff check backend/            # or `ruff check .` inside backend/
python -m mypy backend/
```

Ruff config: `line-length = 120`, `select = E,F,I,W,B,UP,SIM` (ignore `E501`,
`B008`). Import sorting is enforced — run `ruff check --fix` before pushing.

## Android

There is no on-device test suite yet; correctness is exercised through the
backend integration tests plus an interactive two-account workflow:

1. Account A creates a mediation, reads the invite code.
2. Account B joins via the code.
3. Both parties run caucuses + AI analysis, vote, ratify, download the PDF.

## Writing a new backend test

Follow the existing pattern in `backend/accounts/tests.py`:

```python
class MyFeatureTests(APITestCase):
    def setUp(self):
        self.url = "/api/..."      # client is self.client

    def test_happy_path(self):
        resp = self.client.post(self.url, {...}, format="json")
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED)
        self.assertIn("access", resp.json())

    def test_invalid_input(self):
        resp = self.client.post(self.url, {}, format="json")
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
```

Notes:

- Tests run against an isolated throw-away database.
- New accounts are **email-unverified by default** — if a test needs to log in,
  set `user.email_verified = True` after register (see `register_and_verify` in
  `accounts/tests.py`).
- Prefer posting a fresh user per test to keep cases independent.