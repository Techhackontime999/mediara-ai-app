"""Idempotently create (or update) a Django superuser from environment variables.

Reads:
    DJANGO_SUPERUSER_EMAIL      required — superuser login (email)
    DJANGO_SUPERUSER_NAME       optional — full/display name, defaults to email prefix
    DJANGO_SUPERUSER_PASSWORD   required — strong password

Why this exists
    ``manage.py createsuperuser`` is interactive and cannot run unattended in a
    container or on first boot. This command bootstraps the first admin purely
    from environment variables so provisioning works the same way on bare metal,
    Docker, and CI — no SSH into containers, no interactive prompts.

    It is idempotent: running it many times does nothing once the superuser
    exists (unless ``--force`` is given, which syncs the password to the env
    value). The created account is marked ``email_verified``.

Usage:
    DJANGO_SUPERUSER_EMAIL=admin@example.com \
    DJANGO_SUPERUSER_NAME="Mediara Admin" \
    DJANGO_SUPERUSER_PASSWORD='...' \
    python manage.py ensure_superuser [--force]

The Docker entrypoint and docker-compose run this automatically after migrate.
Security note: rotate/remove DJANGO_SUPERUSER_PASSWORD from the environment
after first boot, or keep it in a secret manager (never a public repo).
"""

import os

from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand, CommandError
from django.utils.safestring import mark_safe


class Command(BaseCommand):
    help = "Create or update the admin superuser from DJANGO_SUPERUSER_* env vars."

    def add_arguments(self, parser):
        parser.add_argument(
            "--force",
            action="store_true",
            help="Update an existing superuser's password to match the env value.",
        )

    def handle(self, *args, **options):
        email = (os.environ.get("DJANGO_SUPERUSER_EMAIL") or "").strip().lower()
        password = os.environ.get("DJANGO_SUPERUSER_PASSWORD") or ""
        name = (os.environ.get("DJANGO_SUPERUSER_NAME") or "").strip()

        # No env config -> nothing to do. This keeps default container boots
        # (without superuser vars) working while still bootstrap-friendly.
        if not email and not password:
            self.stdout.write("DJANGO_SUPERUSER_EMAIL/PASSWORD not set; skipping superuser bootstrap.")
            return

        if not email or not password:
            raise CommandError(
                "Set BOTH DJANGO_SUPERUSER_EMAIL and DJANGO_SUPERUSER_PASSWORD "
                "(DJANGO_SUPERUSER_NAME is optional). See docs/CONFIGURATION.md."
            )

        User = get_user_model()
        existing = User.objects.filter(email=email).first()

        if existing and not options["force"]:
            self.stdout.write(
                self.style.WARNING(f"Superuser {email} already exists (use --force to sync its password).")
            )
            return

        if existing:
            existing.set_password(password)
            if name:
                existing.name = name
            existing.is_staff = True
            existing.is_superuser = True
            existing.email_verified = True
            existing.is_active = True
            existing.save(update_fields=["password", "name", "is_staff", "is_superuser", "email_verified", "is_active"])
            self.stdout.write(self.style.SUCCESS(f"Superuser {email} updated (password synced from env)."))
            return

        if not name:
            name = email.split("@")[0]

        User.objects.create_superuser(
            email=email,
            name=name,
            password=password,
            email_verified=True,
            is_active=True,
        )
        self.stdout.write(self.style.SUCCESS(f"Superuser {email} created from environment variables."))
        self.stdout.write(mark_safe("Remember to <b>remove DJANGO_SUPERUSER_PASSWORD</b> from the environment "
                                    "after the first successful boot."))
