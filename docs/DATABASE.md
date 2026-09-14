# Databases — local, PostgreSQL, Supabase, MySQL, MongoDB

Mediara uses a **two-store** strategy.

1. **Relational (Django ORM)** — every account, mediation, caucus message,
   proposal, vote, agreement, audit event and notification lives here. This is
   the single source of truth and the only thing the ORM touches.
2. **Optional auxiliary (MongoDB)** — unstructured/high-volume or analytics
   data (chat-history snapshots, ML feature rows) that does *not* belong in the
   ORM. Completely optional; disabled when `MONGO_URL` is empty.

It is never both-or-nothing: Mongo can be off while the relational store is a
cloud Postgres, and vice-versa.

---

## 1. Pick a relational engine

| `DB_ENGINE` | Local dev | Production | Notes |
| ----------- | --------- | ---------- | ----- |
| `sqlite` (default) | ✅ | dev only | Zero config. Single writer. |
| `postgres` | ✅ | ✅ recommended | The **default choice** — Supabase, Neon, RDS, Cloud SQL, Fly all speak Postgres. |
| `mysql` | ✅ | ✅ | MySQL 8 / MariaDB 10.5+; classic shared-host option. |

### SQLite — zero configuration (default)

Nothing to do: `python manage.py migrate` creates `backend/db.sqlite3`. Relative
`sqlite://`/`DATABASE_URL` paths resolve against `backend/`.

### PostgreSQL — Supabase, Neon, RDS, Cloud SQL, local

**Option A — classic env vars (what `docker-compose.yml` does):**

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `DB_ENGINE` | `sqlite` | `postgres` |
| `POSTGRES_DB` | `mediara` | Database name |
| `POSTGRES_USER` | `mediara` | Role |
| `POSTGRES_PASSWORD` | `mediara` | **MUST set in prod** |
| `POSTGRES_HOST` | `localhost` | Also implicitly enables Postgres (legacy behaviour) |
| `POSTGRES_PORT` | `5432` | Listen port |
| `POSTGRES_SSLMODE` | empty | `disable` \| `allow` \| `prefer` \| `require` \| `verify-full` |
| `POSTGRES_CONN_MAX_AGE` | `0` | Keep-alive seconds (use `60` in prod) |
| `POSTGRES_SSLMODE` | empty | TLS mode — managed clouds usually need `require` |

**Option B — one URL (12-factor, recommended for hosted DBs):**

```bash
# Supabase — Postgres under the hood; take these from Project → Connect → URI
DATABASE_URL=postgres://postgres.yourref:YOUR_PGPASSWORD@aws-0-us-east-1.pooler.supabase.com:6543/postgres?sslmode=require

# Neon — Serverless Postgres
DATABASE_URL=postgres://alex:yourpassword@ep-ivy-aurora-1234.us-east-2.aws.neon.tech/mediara?sslmode=require

# Amazon RDS / Aurora
DATABASE_URL=postgres://mediara:pass@my-db.abcdefgh123.us-east-1.rds.amazonaws.com:5432/mediara?sslmode=require

# Supabase URI can also be pasted as-is:
DATABASE_URL=postgresql://postgres.XXXX:pass@aws-0-region.pooler.supabase.com:5432/postgres?sslmode=require
```

`DATABASE_URL` wins over the individual `POSTGRES_*` variablescars. Neon/Supabase
SSL config is passed through in order: URL query params → `POSTGRES_SSLMODE`.

### MySQL / MariaDB — env vars or URL

```bash
# env vars
DB_ENGINE=mysql
MYSQL_DB=mediara
MYSQL_USER=mediara
MYSQL_PASSWORD=change-me
MYSQL_HOST=localhost
MYSQL_PORT=3306

# or a URL
DATABASE_URL=mysql://mediara:pass@db.example.com:3306/mediara
```

Uses `pymysql` (pure-Python, no C compiler needed) with `utf8mb4` + strict mode.

---

## 2. Migrate between engines

Because everything is env-driven, switching engines is just changing variables —
the data model is identical (SQLite/Postgres/MySQL schemas are the same Django
fields). Keep in mind these are *operational* switches:

> The *migration of existing rows* between engines is a data-migration exercise.
> The project ships `dumpdata`/`loaddata` support so you can move data, but for
> production you normally start fresh on the cloud DB (create tables with
> `migrate`, then import audit-critical rows with `dumpdata --natural-foreign`).

## 3. MongoDB — optional auxiliary store

Set these (in `.env` for local, or container env):

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `MONGO_URL` | empty | Enables Mongo. e.g. `mongodb://127.0.0.1:27017` (local), `mongodb+srv://user:pass@cluster.mongodb.net` (Atlas), DigitalOcean `mongodb://...` |
| `MONGO_DB` | `mediara` | Database name inside the Mongo server |

The helper lives in `backend/config/mongo.py`:

```python
from config.mongo import get_mongo_db, get_mongo_client, mongo_available

client = get_mongo_client()      # cached MongoClient or None
db = get_mongo_db()              # Pymongo Database or None
if mongo_available():            # True when reachable
    db.your_collection.insert_one({...})
```

Mongo *does not* replace the ORM database. Feature code that needs it should
handle `None` gracefully — see `accounts/views.py` (GDPR export) and
`config/health.py` for example usage.

### MongoDB Atlas (cloud)

```bash
# Create a free M0 cluster, add the IP/network allowlist, then:
MONGO_URL=mongodb+srv://user:pass@cluster0.xxxxx.mongodb.net
MONGO_DB=mediara
```

### MongoDB local (Windows)

```powershell
# 1. Install via https://www.mongodb.com/try/download/community
# 2. Create the data dir and start a dev instance:
mkdir C:\data\db -Force
# run mongod once (add --install to register a Windows service)
& "C:\Program Files\MongoDB\Server\7.0\bin\mongod.exe" --dbpath C:\data\db
# leave MONGO_URL unset until you want this optional store enabled
```

---

## 4. Production checklist

- **PostgreSQL** for the ORM database (not SQLite). Prefer Supabase/Neon/RDS
  and let `DATABASE_URL` carry the connection incl. `sslmode=require`.
- Back up the database (`pg_dump` / SQLite copy / `dumpdata`). Automated + offsite.
- Keep `POSTGRES_CONN_MAX_AGE` >= `60` and consider a connection pooler
  (Supabase Transaction Pooler / PgBouncer) if you get connection pressure.
- Optional: set up MongoDB for analytics/aux data; keep the Mongo auth password
  in your environment, never in the repo.
- All connection info is read at boot from env — there is **no** hardcoded DB
  credential in the repository. See `backend/.env.example` + `docs/CONFIGURATION.md`.

## Verification

```bash
python manage.py check_db          # ORM DB + optional Mongo connectivity
curl http://127.0.0.1:8000/health/ # includes "mongo" field
```
