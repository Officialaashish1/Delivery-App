import uuid
from decimal import Decimal

from django.conf import settings
from django.core.cache import cache
from django.db import models
from django.utils import timezone


class TimeStampedModel(models.Model):
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True


class SoftDeleteQuerySet(models.QuerySet):
    def alive(self):
        return self.filter(deleted_at__isnull=True)

    def dead(self):
        return self.filter(deleted_at__isnull=False)

    def delete(self):
        return super().update(deleted_at=timezone.now())

    def hard_delete(self):
        return super().delete()


class SoftDeleteManager(models.Manager):
    def get_queryset(self):
        return SoftDeleteQuerySet(self.model, using=self._db).alive()


class AllObjectsManager(models.Manager):
    def get_queryset(self):
        return SoftDeleteQuerySet(self.model, using=self._db)


class SoftDeleteModel(models.Model):
    deleted_at = models.DateTimeField(null=True, blank=True, db_index=True)

    objects = SoftDeleteManager()
    all_objects = AllObjectsManager()

    class Meta:
        abstract = True

    def delete(self, using=None, keep_parents=False):
        self.deleted_at = timezone.now()
        self.save(update_fields=["deleted_at"])

    def hard_delete(self):
        super().delete()

    def restore(self):
        self.deleted_at = None
        self.save(update_fields=["deleted_at"])


class UUIDModel(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)

    class Meta:
        abstract = True


class MoneyField(models.DecimalField):
    """Decimal money field with fixed precision. Never use floats for money. Non-negative by default."""

    def __init__(self, *args, **kwargs):
        from django.core.validators import MinValueValidator

        kwargs.setdefault("max_digits", 12)
        kwargs.setdefault("decimal_places", 2)
        kwargs.setdefault("default", Decimal("0.00"))
        if not kwargs.pop("allow_negative", False):
            kwargs.setdefault("validators", [MinValueValidator(Decimal("0"))])
        super().__init__(*args, **kwargs)


class PlatformSettings(models.Model):
    """Singleton row of business-configurable values editable from the admin dashboard."""

    SINGLETON_ID = 1
    CACHE_KEY = "platform_settings"

    id = models.PositiveSmallIntegerField(primary_key=True, default=SINGLETON_ID, editable=False)
    default_commission_percent = models.DecimalField(max_digits=5, decimal_places=2, default=Decimal("20.00"))
    gst_percent = models.DecimalField(max_digits=5, decimal_places=2, default=Decimal("5.00"))
    platform_fee = MoneyField(default=Decimal("5.00"))
    base_delivery_fee = MoneyField(default=Decimal("25.00"))
    base_delivery_km = models.DecimalField(max_digits=5, decimal_places=2, default=Decimal("2.00"))
    per_km_delivery_fee = MoneyField(default=Decimal("8.00"))
    free_delivery_above = MoneyField(default=Decimal("0.00"), help_text="0 disables free delivery")
    max_delivery_radius_km = models.DecimalField(max_digits=5, decimal_places=2, default=Decimal("10.00"))
    rider_base_pay = MoneyField(default=Decimal("20.00"))
    rider_per_km_pay = MoneyField(default=Decimal("6.00"))
    rider_search_radius_km = models.DecimalField(max_digits=5, decimal_places=2, default=Decimal("6.00"))
    rider_offer_timeout_seconds = models.PositiveIntegerField(default=45)
    rider_max_concurrent_orders = models.PositiveSmallIntegerField(default=1)
    restaurant_accept_timeout_minutes = models.PositiveIntegerField(default=10)
    customer_cancel_window_seconds = models.PositiveIntegerField(default=60, help_text="Free cancel window after placing")
    cancellation_fee_after_accept = MoneyField(default=Decimal("0.00"))
    cod_enabled = models.BooleanField(default=True)
    cod_max_order_value = MoneyField(default=Decimal("2000.00"))
    min_app_version_customer = models.CharField(max_length=20, default="1.0.0")
    min_app_version_rider = models.CharField(max_length=20, default="1.0.0")
    min_app_version_partner = models.CharField(max_length=20, default="1.0.0")
    support_phone = models.CharField(max_length=20, blank=True, default="")
    support_email = models.EmailField(blank=True, default="")
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Platform settings"
        verbose_name_plural = "Platform settings"

    def __str__(self):
        return "Platform settings"

    def save(self, *args, **kwargs):
        self.id = self.SINGLETON_ID
        super().save(*args, **kwargs)
        cache.delete(self.CACHE_KEY)

    @classmethod
    def load(cls) -> "PlatformSettings":
        obj = cache.get(cls.CACHE_KEY)
        if obj is None:
            obj, _ = cls.objects.get_or_create(id=cls.SINGLETON_ID)
            cache.set(cls.CACHE_KEY, obj, 300)
        return obj


class AuditLog(models.Model):
    """Immutable record of sensitive actions (admin changes, money movement, status changes)."""

    actor = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
                              related_name="audit_logs")
    action = models.CharField(max_length=64, db_index=True)
    target_type = models.CharField(max_length=64, blank=True, default="", db_index=True)
    target_id = models.CharField(max_length=64, blank=True, default="", db_index=True)
    metadata = models.JSONField(default=dict, blank=True)
    ip_address = models.GenericIPAddressField(null=True, blank=True)
    user_agent = models.CharField(max_length=256, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [models.Index(fields=["target_type", "target_id", "-created_at"])]

    def __str__(self):
        return f"{self.action} {self.target_type}:{self.target_id}"
