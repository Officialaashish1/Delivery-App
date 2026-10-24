import uuid
from decimal import Decimal

from django.conf import settings
from django.db import models
from django.utils import timezone

from apps.core.models import MoneyField, TimeStampedModel
from apps.restaurants.models import Restaurant


class DiscountType(models.TextChoices):
    PERCENT = "percent", "Percentage"
    FIXED = "fixed", "Fixed amount"
    FREE_DELIVERY = "free_delivery", "Free delivery"


class Coupon(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    code = models.CharField(max_length=30, unique=True)
    title = models.CharField(max_length=80)
    description = models.CharField(max_length=255, blank=True, default="")
    discount_type = models.CharField(max_length=15, choices=DiscountType.choices, default=DiscountType.PERCENT)
    value = models.DecimalField(max_digits=10, decimal_places=2, default=Decimal("0"),
                                help_text="Percent (0-100) or fixed amount")
    min_order_amount = MoneyField(default=Decimal("0.00"))
    max_discount = MoneyField(null=True, blank=True, default=None, help_text="Cap for percentage coupons")
    first_order_only = models.BooleanField(default=False)
    restaurant = models.ForeignKey(Restaurant, null=True, blank=True, on_delete=models.CASCADE, related_name="coupons",
                                   help_text="Blank = platform-wide")
    funded_by_restaurant = models.BooleanField(default=False, help_text="Restaurant bears the discount cost")
    usage_limit_total = models.PositiveIntegerField(null=True, blank=True)
    usage_limit_per_user = models.PositiveIntegerField(default=1)
    used_count = models.PositiveIntegerField(default=0)
    starts_at = models.DateTimeField(default=timezone.now)
    expires_at = models.DateTimeField(null=True, blank=True)
    is_active = models.BooleanField(default=True)
    is_public = models.BooleanField(default=True, help_text="Show in the 'available offers' list")

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return self.code

    def save(self, *args, **kwargs):
        self.code = self.code.strip().upper()
        super().save(*args, **kwargs)

    @property
    def is_live(self):
        now = timezone.now()
        if not self.is_active or self.starts_at > now:
            return False
        if self.expires_at and self.expires_at < now:
            return False
        if self.usage_limit_total is not None and self.used_count >= self.usage_limit_total:
            return False
        return True

    def short_text(self):
        if self.discount_type == DiscountType.PERCENT:
            cap = f" up to ₹{self.max_discount:.0f}" if self.max_discount else ""
            return f"{self.value:.0f}% OFF{cap}"
        if self.discount_type == DiscountType.FIXED:
            return f"₹{self.value:.0f} OFF"
        return "FREE DELIVERY"


class CouponUsage(models.Model):
    coupon = models.ForeignKey(Coupon, on_delete=models.CASCADE, related_name="usages")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="coupon_usages")
    order = models.OneToOneField("orders.Order", on_delete=models.CASCADE, related_name="coupon_usage")
    discount_amount = MoneyField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        indexes = [models.Index(fields=["coupon", "user"])]
