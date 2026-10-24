import uuid

from django.conf import settings
from django.db import models

from apps.core.models import TimeStampedModel
from apps.core.validators import validate_image_upload


class TicketCategory(models.TextChoices):
    MISSING_ITEM = "missing_item", "Missing item"
    WRONG_ORDER = "wrong_order", "Wrong order"
    QUALITY = "quality", "Food quality"
    LATE = "late_delivery", "Late delivery"
    REFUND = "refund", "Refund request"
    PAYMENT = "payment", "Payment issue"
    RIDER = "rider_behaviour", "Rider behaviour"
    CUSTOMER_UNAVAILABLE = "customer_unavailable", "Customer unavailable"
    RESTAURANT_DELAY = "restaurant_delay", "Restaurant delay"
    VEHICLE = "vehicle_issue", "Vehicle issue"
    EMERGENCY = "emergency", "Emergency"
    ACCOUNT = "account", "Account"
    OTHER = "other", "Other"


class TicketStatus(models.TextChoices):
    OPEN = "open", "Open"
    IN_PROGRESS = "in_progress", "In progress"
    WAITING_CUSTOMER = "waiting", "Waiting for reply"
    RESOLVED = "resolved", "Resolved"
    CLOSED = "closed", "Closed"


class TicketPriority(models.TextChoices):
    LOW = "low", "Low"
    NORMAL = "normal", "Normal"
    HIGH = "high", "High"
    URGENT = "urgent", "Urgent"


class SupportTicket(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    ticket_number = models.CharField(max_length=12, unique=True, blank=True)
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="tickets")
    order = models.ForeignKey("orders.Order", null=True, blank=True, on_delete=models.SET_NULL, related_name="tickets")
    category = models.CharField(max_length=24, choices=TicketCategory.choices)
    subject = models.CharField(max_length=140)
    description = models.TextField()
    status = models.CharField(max_length=12, choices=TicketStatus.choices, default=TicketStatus.OPEN, db_index=True)
    priority = models.CharField(max_length=8, choices=TicketPriority.choices, default=TicketPriority.NORMAL)
    assigned_to = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
                                    related_name="assigned_tickets")
    resolution = models.TextField(blank=True, default="")
    resolved_at = models.DateTimeField(null=True, blank=True)
    refund_amount = models.DecimalField(max_digits=12, decimal_places=2, null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]

    def save(self, *args, **kwargs):
        if not self.ticket_number:
            import secrets

            self.ticket_number = "T" + str(secrets.randbelow(10**7)).zfill(7)
        if self.category == TicketCategory.EMERGENCY:
            self.priority = TicketPriority.URGENT
        super().save(*args, **kwargs)

    def __str__(self):
        return self.ticket_number


class TicketMessage(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    ticket = models.ForeignKey(SupportTicket, on_delete=models.CASCADE, related_name="messages")
    sender = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="+")
    is_staff_reply = models.BooleanField(default=False)
    text = models.TextField()
    attachment = models.ImageField(upload_to="support/%Y/%m/", blank=True, null=True,
                                   validators=[validate_image_upload])
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["created_at"]


class FAQ(models.Model):
    audience = models.CharField(max_length=10, default="customer",
                                choices=[("customer", "Customer"), ("rider", "Rider"), ("partner", "Partner")])
    question = models.CharField(max_length=255)
    answer = models.TextField()
    sort_order = models.PositiveSmallIntegerField(default=0)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["audience", "sort_order"]
        verbose_name = "FAQ"
        verbose_name_plural = "FAQs"

    def __str__(self):
        return self.question
