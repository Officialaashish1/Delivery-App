import uuid

from django.conf import settings
from django.db import models

from apps.core.models import MoneyField, TimeStampedModel


class PaymentStatus(models.TextChoices):
    CREATED = "created", "Created"
    AUTHORIZED = "authorized", "Authorized"
    CAPTURED = "captured", "Captured"
    FAILED = "failed", "Failed"
    REFUNDED = "refunded", "Refunded"
    PARTIALLY_REFUNDED = "partially_refunded", "Partially refunded"
    COD_PENDING = "cod_pending", "COD - to be collected"
    COD_COLLECTED = "cod_collected", "COD - collected"


class Payment(TimeStampedModel):
    """One row per payment attempt. Provider ids let us reconcile with the gateway; no card data is stored."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    order = models.ForeignKey("orders.Order", on_delete=models.PROTECT, related_name="payments")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="payments")
    provider = models.CharField(max_length=20, default="razorpay")
    method = models.CharField(max_length=12)
    amount = MoneyField()
    currency = models.CharField(max_length=3, default="INR")
    status = models.CharField(max_length=20, choices=PaymentStatus.choices, default=PaymentStatus.CREATED,
                              db_index=True)
    provider_order_id = models.CharField(max_length=64, blank=True, default="", db_index=True)
    provider_payment_id = models.CharField(max_length=64, blank=True, default="", db_index=True)
    provider_signature = models.CharField(max_length=256, blank=True, default="")
    failure_reason = models.CharField(max_length=255, blank=True, default="")
    raw_response = models.JSONField(default=dict, blank=True)
    captured_at = models.DateTimeField(null=True, blank=True)
    collected_by = models.ForeignKey("riders.RiderProfile", null=True, blank=True, on_delete=models.SET_NULL,
                                     related_name="cod_collections")

    class Meta:
        ordering = ["-created_at"]
        constraints = [models.UniqueConstraint(fields=["provider", "provider_payment_id"],
                                               condition=~models.Q(provider_payment_id=""),
                                               name="uniq_provider_payment")]

    def __str__(self):
        return f"{self.order.order_number} {self.method} {self.amount} {self.status}"


class RefundStatus(models.TextChoices):
    PENDING = "pending", "Pending"
    PROCESSING = "processing", "Processing"
    COMPLETED = "completed", "Completed"
    FAILED = "failed", "Failed"


class Refund(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    order = models.ForeignKey("orders.Order", on_delete=models.PROTECT, related_name="refunds")
    payment = models.ForeignKey(Payment, null=True, blank=True, on_delete=models.PROTECT, related_name="refunds")
    amount = MoneyField()
    reason = models.CharField(max_length=255)
    status = models.CharField(max_length=12, choices=RefundStatus.choices, default=RefundStatus.PENDING)
    provider_refund_id = models.CharField(max_length=64, blank=True, default="")
    initiated_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
                                     related_name="+")
    processed_at = models.DateTimeField(null=True, blank=True)
    failure_reason = models.CharField(max_length=255, blank=True, default="")
    raw_response = models.JSONField(default=dict, blank=True)

    class Meta:
        ordering = ["-created_at"]


class WebhookEvent(models.Model):
    """Idempotency log for provider webhooks: an event id is processed at most once."""

    provider = models.CharField(max_length=20)
    event_id = models.CharField(max_length=128)
    event_type = models.CharField(max_length=64)
    payload = models.JSONField()
    processed = models.BooleanField(default=False)
    error = models.TextField(blank=True, default="")
    received_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ("provider", "event_id")
