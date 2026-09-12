"""Notification dispatch helpers (in-app rows + optional email)."""

from django.conf import settings
from django.core.mail import send_mail

from .models import Notification


def create_notification(user, *, notification_type="general", title, body="", mediation=None, send_email=True):
    """Persist an in-app notification and (optionally) send an email copy.

    Email is best-effort: the default console backend just prints it, which is
    the expected behaviour for a local development deployment.
    """
    if user is None:
        return None
    notification = Notification.objects.create(
        user=user,
        mediation=mediation,
        notification_type=notification_type,
        title=title,
        body=body,
    )
    if send_email and user.email and not user.data_erased:
        send_mail(
            f"Mediara AI — {title}",
            f"{body}\n\nOpen the Mediara AI app for details.",
            None,
            [user.email],
            fail_silently=True,
        )
    return notification


def notify_participants(mediation, *, notification_type, title_template, body_template, exclude_user=None):
    """Create a notification for every joined participant of a mediation."""
    sent = []
    for participant in mediation.participants.exclude(status="INVITED").select_related("user"):
        if participant.user_id and (exclude_user is None or participant.user_id != exclude_user.pk):
            sent.append(
                create_notification(
                    participant.user,
                    notification_type=notification_type,
                    title=title_template.format(title=mediation.title),
                    body=body_template.format(title=mediation.title),
                    mediation=mediation,
                )
            )
    return sent