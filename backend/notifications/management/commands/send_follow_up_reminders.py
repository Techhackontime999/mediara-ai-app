"""Follow-up reminder engine.

Sends reminders for mediations whose agreement is due for its scheduled
efficacy check-in but has not yet been reported on by every participant.

Usage:
    python manage.py send_follow_up_reminders [--days 14]

Designed to run from cron / Celery beat / CI scheduled job. Every mediation with
a RESOLVED (or FOLLOW_UP) agreement older than `--days` days is examined; each
joined participant who has not submitted a follow-up report for the current
cycle receives an in-app notification and an email.
"""

from datetime import timedelta

from django.core.management.base import BaseCommand
from django.utils import timezone
from mediation.models import Mediation, MediationStatus

from ...services import create_notification


class Command(BaseCommand):
    help = "Send due 14-day follow-up check-in reminders."

    def add_arguments(self, parser):
        parser.add_argument("--days", type=int, default=14)

    def handle(self, *args, **options):
        days = options["days"]
        cutoff = timezone.now() - timedelta(days=days)

        mediations = (
            Mediation.objects.filter(status__in=[MediationStatus.RESOLVED, MediationStatus.FOLLOW_UP])
            .filter(resolved_at__lt=cutoff)
            .select_related("agreement")
            .prefetch_related("participants__user", "followup_reports")
        )

        reminded = 0
        for mediation in mediations:
            if not hasattr(mediation, "agreement"):
                continue
            reported = {
                r.participant_id
                for r in mediation.followup_reports.all()
                if r.created_at >= mediation.resolved_at
            } if mediation.resolved_at else set()
            for participant in mediation.participants.exclude(status="INVITED").select_related("user"):
                if participant.user_id is None or participant.id in reported:
                    continue
                create_notification(
                    participant.user,
                    notification_type="followup_due",
                    title="Your 14-day check-in is due",
                    body=f"The agreement for '{mediation.title}' is due for its efficacy check-in. How is it holding up?",
                    mediation=mediation,
                )
                reminded += 1
                self.stdout.write(f"Reminded {participant.user.email} for mediation {mediation.invite_code}")

        self.stdout.write(self.style.SUCCESS(f"Done. Reminders sent to {reminded} participant(s)."))
