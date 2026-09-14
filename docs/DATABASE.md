# Database

The backend defaults to **SQLite** for development and switches to
**PostgreSQL** the moment `DB_POSTGRES=True` (or `POSTGRES_HOST`) is set.

## Choosing the backend

| Variable | Effect |
| -------- | ------ |
| `DB_POSTGRES=False` | SQLite at `backend/db.sqlite3` (dev default). |
| `DB_POSTGRES=True` | PostgreSQL using `POSTGRES_*` variables. |
| `POSTGRES_SSLMODE=require` | TLS to managed clouds (RDS, Cloud SQL, Neon). |
| `POSTGRES_CONN_MAX_AGE=60` | Persist DB connections between requests. |

## Migrations

```bash
python manage.py makemigrations        # after model changes
python manage.py migrate               # apply
python manage.py showmigrations        # status
```

- Migrations are committed to the repo (`backend/*/migrations/`).
- Never edit an applied migration unless you know the risks; write a new one.
- To regenerate the DB from scratch in dev: delete `db.sqlite3`, then `migrate`.

## PostgreSQL local setup (dev)

```bash
# create the role + database once
CREATE USER mediara WITH PASSWORD 'mediara';
CREATE DATABASE mediara OWNER mediara;

# then in .env
DB_POSTGRES=True
POSTGRES_DB=mediara
POSTGRES_USER=mediara
POSTGRES_PASSWORD=mediara
POSTGRES_HOST=localhost
```

Docker Compose starts PostgreSQL automatically (named volume, survives
`down`/`up`).

## Schema at a glance

Django apps, each owning its tables:

```
accounts      users (custom, email-based), MFA secrets, audit events
mediation     cases, invite codes, participants
conversations private caucuses (encrypted content column)
ai            (stateless — grounding/balance computed on the fly)
resolutions   conflict analysis, proposals, votes
agreements    accords, document fingerprints, follow-ups
notifications in-app notifications
audit         append-only audit trail
```

Model changes under `backend/` (e.g. `accounts/models.py`) require a
`makemigrations` before tests pass.

## Backups

**SQLite (dev):**

```bash
python manage.py dumpdata --natural-foreign --exclude=contenttypes > backup.json
```

**PostgreSQL (prod):**

```bash
pg_dump -h localhost -U mediara mediara | gzip > mediara-$(date +%F).sql.gz
```

- Schedule daily dumps and keep them off-box.
- **Encrypted private messages** are only readable with `ENCRYPTION_KEY`.
  Back up the key together with (ideally separately from) your dumps. Losing
  the key = unrecoverable message contents by design.

## Reset for tests

The test runner creates an isolated test database and destroys it after the
run — no manual setup required.