import uuid

from django.conf import settings
from django.db import models


class NotificationCategory(models.TextChoices):
    ORDER = "order", "Order"
    PAYMENT = "payment", "Payment"
    PROMO = "promo", "Promotion"
    ACCOUNT = "account", "Account"
    SYSTEM = "system", "System"


class Notification(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="notifications")
    category = models.CharField(max_length=10, choices=NotificationCategory.choices, default=NotificationCategory.ORDER)
    title = models.CharField(max_length=120)
    body = models.CharField(max_length=500)
    data = models.JSONField(default=dict, blank=True)
    dedupe_key = models.CharField(max_length=120, blank=True, default="")
    is_read = models.BooleanField(default=False)
    push_sent = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [models.Index(fields=["user", "is_read", "-created_at"])]
        constraints = [models.UniqueConstraint(fields=["user", "dedupe_key"], condition=~models.Q(dedupe_key=""),
                                               name="uniq_notification_dedupe")]


class PromoCampaign(models.Model):
    """Admin-triggered promotional push to a segment of users."""

    title = models.CharField(max_length=120)
    body = models.CharField(max_length=500)
    deep_link = models.CharField(max_length=255, blank=True, default="")
    audience = models.CharField(max_length=10, default="customer",
                                choices=[("customer", "Customers"), ("rider", "Riders"), ("partner", "Partners")])
    city = models.CharField(max_length=80, blank=True, default="")
    sent_count = models.PositiveIntegerField(default=0)
    sent_at = models.DateTimeField(null=True, blank=True)
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL, related_name="+")
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.title
