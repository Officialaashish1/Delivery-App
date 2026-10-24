import uuid

from django.conf import settings
from django.db import models

from apps.core.models import MoneyField, TimeStampedModel


class SettlementStatus(models.TextChoices):
    PENDING = "pending", "Pending"
    PROCESSING = "processing", "Processing"
    PAID = "paid", "Paid"
    FAILED = "failed", "Failed"
    ON_HOLD = "on_hold", "On hold"


class RestaurantSettlement(TimeStampedModel):
    """A payout batch for a restaurant covering a date range. Built from SettlementLines."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    restaurant = models.ForeignKey("restaurants.Restaurant", on_delete=models.PROTECT, related_name="settlements")
    period_start = models.DateField()
    period_end = models.DateField()
    order_count = models.PositiveIntegerField(default=0)
    gross_sales = MoneyField(help_text="Item total + packaging across orders")
    commission_amount = MoneyField()
    coupon_deductions = MoneyField(help_text="Restaurant-funded coupon discounts")
    refund_deductions = MoneyField()
    adjustments = MoneyField(help_text="Manual +/- adjustments", allow_negative=True)
    cod_collected_by_platform = MoneyField(help_text="Informational: COD cash riders collected for these orders")
    net_payable = MoneyField(allow_negative=True)
    status = models.CharField(max_length=12, choices=SettlementStatus.choices, default=SettlementStatus.PENDING)
    reference = models.CharField(max_length=80, blank=True, default="")
    note = models.CharField(max_length=255, blank=True, default="")
    processed_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
                                     related_name="+")
    processed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-period_end"]

    def __str__(self):
        return f"{self.restaurant.name} {self.period_start}..{self.period_end} ₹{self.net_payable}"


class SettlementLine(models.Model):
    """Per-order financial record for the restaurant. Created once at delivery; refunds adjust later."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    restaurant = models.ForeignKey("restaurants.Restaurant", on_delete=models.PROTECT, related_name="settlement_lines")
    order = models.OneToOneField("orders.Order", on_delete=models.PROTECT, related_name="settlement_line")
    settlement = models.ForeignKey(RestaurantSettlement, null=True, blank=True, on_delete=models.SET_NULL,
                                   related_name="lines")
    gross = MoneyField()
    commission_percent = models.DecimalField(max_digits=5, decimal_places=2)
    commission = MoneyField()
    coupon_deduction = MoneyField()
    refund_deduction = MoneyField()
    tax_collected = MoneyField()
    net = MoneyField(allow_negative=True)
    is_cod = models.BooleanField(default=False)
    delivered_at = models.DateTimeField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-delivered_at"]
        indexes = [models.Index(fields=["restaurant", "settlement"])]


class PlatformLedger(models.Model):
    """Daily roll-up of platform revenue for reports."""

    date = models.DateField(unique=True)
    orders = models.PositiveIntegerField(default=0)
    gross_order_value = MoneyField()
    commission_revenue = MoneyField()
    delivery_fee_revenue = MoneyField()
    platform_fee_revenue = MoneyField()
    coupon_cost = MoneyField(help_text="Platform-funded discounts")
    rider_payout_cost = MoneyField()
    refunds = MoneyField()
    net_revenue = MoneyField(allow_negative=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-date"]
