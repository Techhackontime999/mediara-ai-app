# Modifying the app

Everything you need to extend the platform — backend first, Android second.

## Mental model

One Django codebase (`backend/`) exposing a single authenticated REST API under
`/api/`, consumed by one Android client (`app/`). Feature work usually touches a
Django app (`accounts`, `mediation`, …) **and** the matching Android screens.
The API is the contract between them.

## Adding a backend endpoint

1. **Model** in the right Django app (`backend/<app>/models.py`).
2. **Migration**: `python manage.py makemigrations <app> && migrate`.
3. **Serializer** in `backend/<app>/serializers.py`.
4. **View** in `backend/<app>/views.py` (DRF `APIView`/`generics`).
5. **Route** in `backend/<app>/urls.py` (mounted under `/api/<app>/`).
6. **Test** in `backend/<app>/tests.py` (see [TESTING.md](TESTING.md)).
7. Lint: `python -m ruff check backend/`.

### Convention notes

- Auth = `IsAuthenticated` + `JWTAuthentication`; public routes opt in with
  `permission_classes = [AllowAny]` and get throttled.
- Camel-case **keys on the wire** (`emailVerified`), Pythonic snake_case in
  models. `serializers.Serializer(Meta)` or explicit fields handle the mapping.
- Audit significant actions: `AuditLog.record(actor, action, entity_type, entity_id, request)`.
- Private/message content must use `security.fields.EncryptedTextField`
  (encrypted at rest; never logged).

## Hooking into the AI layer

`backend/ai/` is provider-agnostic. To give the model more context or new
outputs:

- Adjust the **prompt builder / pipeline** in `backend/ai/` (analysis,
  grounding, balance).
- Keep outputs **grounded**: annotate citations (`grounded_in`) to the exact
  participant statements.
- The deterministic fallback must produce a shape compatible with what the
  Android client renders. If your feature needs new fields, ship them in both
  LLM and fallback outputs.

## Adding a management command

1. Create `backend/<app>/management/__init__.py` and
   `backend/<app>/management/commands/__init__.py` (empty).
2. Create `backend/<app>/management/commands/<name>.py` subclassing
   `BaseCommand`. See `accounts/ensure_superuser.py` or
   `notifications/send_follow_up_reminders.py` for the pattern.
3. Run: `python manage.py <name>`.

## Adding an Android screen

1. Register the route in `app/.../ui/navigation/Screen.kt` — add the screen
   object and choose its motion group:
   - `BottomBarRoutes` → slide/fade tab navigation.
   - `CrossfadeRoutes` → crossfade.
   - `RiseRoutes` → rise-from-bottom (auth/overlay).
2. Add the `composableWithMotion(Screen.X)` call in
   `app/.../ui/navigation/AppNavigation.kt` (with destinations, nav args).
3. Build the screen composable under `app/.../ui/` following the existing
   patterns (Material 3, theme from `ui/theme/`, `ViewModel` + repository for
   data).
4. `.\gradlew.bat assembleDebug` to verify compilation.

## Changing the API contract

- Update the DTOs in `app/.../data/remote/` and the mapper in
  `app/.../data/MediaraMappers.kt` to match new serializer fields.
- Keep `MediaraApiService` in lock-step with `urls.py`.
- Add/refresh tests and the Swagger docs (`/api/docs/` updates automatically).

## Configuration & wiring

New environment variables belong in three places:

1. `backend/config/settings.py` (read via `os.environ.get` / `env_bool` /
   `env_list`).
2. `backend/.env.example` (documented, with a sensible default).
3. `docker-compose.yml` + `Dockerfile` (pass through into the container, with
   `${VAR:-default}` so nothing breaks).

Then document it in [CONFIGURATION.md](CONFIGURATION.md).

## Commands you will use constantly

```bash
# backend
python manage.py check && python manage.py test
python -m ruff check . --fix
python manage.py ensure_superuser

# android
.\gradlew.bat assembleDebug
```

## CI gate

`.github/workflows/ci.yml` runs `check`, fresh-DB migrate, the full test suite,
ruff, and (informational) mypy on every push to `main`. Keep it green.