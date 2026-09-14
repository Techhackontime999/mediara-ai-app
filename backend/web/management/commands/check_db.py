"""Report connectivity of every configured database.

  python manage.py check_db

Checks the Django ORM database (SQLite / PostgreSQL / MySQL) with ``SELECT 1``
and, when ``MONGO_URL`` is set, pings MongoDB. Exits with status 1 if any
*configured* store is unreachable (Mongo is only checked when enabled).
"""

from django.core.management.base import BaseCommand
from django.db import connection


class Command(BaseCommand):
    help = "Verify connectivity to the configured database(s)."

    def handle(self, *args, **options):  # noqa: ARG002
        ok = True

        meta = connection.vendor
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT 1")
                cursor.fetchone()
            self.stdout.write(self.style.SUCCESS(f"ORM database [{meta}] ......... ok"))
        except Exception as exc:  # noqa: BLE001 - surface any connectivity error
            self.stderr.write(self.style.ERROR(f"ORM database [{meta}] ......... FAILED: {exc}"))
            ok = False

        try:
            from config.mongo import get_mongo_db
        except ImportError:
            get_mongo_db = None

        if get_mongo_db and get_mongo_db():
            try:
                db = get_mongo_db()
                db.client.admin.command("ping", serverSelectionTimeoutMS=5000)
                self.stdout.write(self.style.SUCCESS(f"MongoDB [{db.name}] ........... ok"))
            except Exception as exc:  # noqa: BLE001 - surface any connectivity error
                self.stderr.write(self.style.ERROR(f"MongoDB ....................... FAILED: {exc}"))
                ok = False
        else:
            self.stdout.write("MongoDB (auxiliary) ...... not configured (set MONGO_URL to enable)")

        if not ok:
            raise SystemExit(1)
