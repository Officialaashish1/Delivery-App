import logging

from celery import shared_task

from apps.accounts.models import Device

from .fcm import send_to_tokens
from .models import Notification

log = logging.getLogger("localrush.notifications")


@shared_task(name="apps.notifications.tasks.send_push", bind=True, max_retries=3, default_retry_delay=10)
def send_push(self, user_id, title, body, data=None, notification_id=None, channel_id="localrush_orders"):
    tokens = list(Device.objects.filter(user_id=user_id, is_active=True).values_list("fcm_token", flat=True))
    if not tokens:
        return 0
    try:
        res = send_to_tokens(tokens, title, body, data or {}, channel_id=channel_id)
    except Exception as exc:  # noqa: BLE001
        log.exception("push failed")
        raise self.retry(exc=exc)
    if res["invalid_tokens"]:
        Device.objects.filter(fcm_token__in=res["invalid_tokens"]).update(is_active=False)
    if notification_id and res["sent"]:
        Notification.objects.filter(pk=notification_id).update(push_sent=True)
    return res["sent"]


@shared_task(name="apps.notifications.tasks.send_campaign")
def send_campaign(campaign_id):
    from django.utils import timezone

    from apps.accounts.models import User

    from .models import NotificationCategory, PromoCampaign
    from .services import notify_user

    c = PromoCampaign.objects.get(pk=campaign_id)
    users = User.objects.filter(role=c.audience, is_active=True, is_suspended=False)
    if c.city:
        if c.audience == "rider":
            users = users.filter(rider_profile__city__iexact=c.city)
        elif c.audience == "customer":
            users = users.filter(addresses__city__iexact=c.city).distinct()
    n = 0
    for uid in users.values_list("id", flat=True).iterator():
        notify_user(uid, c.title, c.body, {"type": "promo", "deep_link": c.deep_link},
                    category=NotificationCategory.PROMO, dedupe_key=f"campaign:{c.id}", channel_id="localrush_promos")
        n += 1
    c.sent_count = n
    c.sent_at = timezone.now()
    c.save(update_fields=["sent_count", "sent_at"])
    return n
