import uuid
from decimal import Decimal

from django.conf import settings
from django.contrib.gis.db import models as gis_models
from django.db import models
from django.utils import timezone
from django.utils.text import slugify

from apps.core.models import MoneyField, SoftDeleteModel, TimeStampedModel
from apps.core.validators import validate_document_upload, validate_image_upload


class Cuisine(models.Model):
    """Cuisine tags (North Indian, Chinese, ...) used for discovery and filtering."""

    name = models.CharField(max_length=60, unique=True)
    slug = models.SlugField(max_length=60, unique=True)
    icon = models.ImageField(upload_to="cuisines/", blank=True, null=True)
    sort_order = models.PositiveSmallIntegerField(default=0)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["sort_order", "name"]

    def __str__(self):
        return self.name

    def save(self, *args, **kwargs):
        if not self.slug:
            self.slug = slugify(self.name)
        super().save(*args, **kwargs)


class RestaurantCategory(models.Model):
    """Home-screen food categories (Pizza, Burger, Biryani, ...)."""

    name = models.CharField(max_length=60, unique=True)
    slug = models.SlugField(max_length=60, unique=True)
    image = models.ImageField(upload_to="categories/", blank=True, null=True)
    sort_order = models.PositiveSmallIntegerField(default=0)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["sort_order", "name"]
        verbose_name_plural = "Restaurant categories"

    def __str__(self):
        return self.name

    def save(self, *args, **kwargs):
        if not self.slug:
            self.slug = slugify(self.name)
        super().save(*args, **kwargs)


class RestaurantStatus(models.TextChoices):
    PENDING = "pending", "Pending verification"
    UNDER_REVIEW = "under_review", "Under review"
    APPROVED = "approved", "Approved"
    REJECTED = "rejected", "Rejected"
    SUSPENDED = "suspended", "Suspended"


class RestaurantType(models.TextChoices):
    RESTAURANT = "restaurant", "Restaurant"
    HOTEL = "hotel", "Hotel"
    CAFE = "cafe", "Cafe"
    CLOUD_KITCHEN = "cloud_kitchen", "Cloud kitchen"
    BAKERY = "bakery", "Bakery"
    SWEETS = "sweets", "Sweets & snacks"
    OTHER = "other", "Other"


class Restaurant(SoftDeleteModel, TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    owner = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="restaurants")
    name = models.CharField(max_length=120, db_index=True)
    slug = models.SlugField(max_length=140, unique=True)
    restaurant_type = models.CharField(max_length=20, choices=RestaurantType.choices, default=RestaurantType.RESTAURANT)
    description = models.TextField(blank=True, default="")
    owner_name = models.CharField(max_length=120)
    phone = models.CharField(max_length=16)
    email = models.EmailField(blank=True, default="")
    logo = models.ImageField(upload_to="restaurants/logos/", blank=True, null=True, validators=[validate_image_upload])
    cover_image = models.ImageField(upload_to="restaurants/covers/", blank=True, null=True,
                                    validators=[validate_image_upload])
    # location
    address_line1 = models.CharField(max_length=255)
    address_line2 = models.CharField(max_length=255, blank=True, default="")
    landmark = models.CharField(max_length=255, blank=True, default="")
    city = models.CharField(max_length=80, db_index=True)
    state = models.CharField(max_length=80, blank=True, default="")
    postal_code = models.CharField(max_length=10, blank=True, default="")
    location = gis_models.PointField(geography=True, srid=4326)
    delivery_radius_km = models.DecimalField(max_digits=5, decimal_places=2, default=Decimal("7.00"))
    # compliance
    fssai_number = models.CharField(max_length=20, blank=True, default="")
    fssai_expiry = models.DateField(null=True, blank=True)
    gst_number = models.CharField(max_length=20, blank=True, default="")
    pan_number = models.CharField(max_length=12, blank=True, default="")
    # banking (for settlements)
    bank_account_name = models.CharField(max_length=120, blank=True, default="")
    bank_account_number = models.CharField(max_length=24, blank=True, default="")
    bank_ifsc = models.CharField(max_length=12, blank=True, default="")
    upi_id = models.CharField(max_length=80, blank=True, default="")
    # business config
    cuisines = models.ManyToManyField(Cuisine, blank=True, related_name="restaurants")
    categories = models.ManyToManyField(RestaurantCategory, blank=True, related_name="restaurants")
    is_pure_veg = models.BooleanField(default=False)
    min_order_amount = MoneyField(default=Decimal("0.00"))
    packaging_charge = MoneyField(default=Decimal("0.00"), help_text="Flat per-order packaging charge")
    avg_preparation_minutes = models.PositiveSmallIntegerField(default=20)
    commission_percent = models.DecimalField(max_digits=5, decimal_places=2, null=True, blank=True,
                                             help_text="Overrides platform default when set")
    cost_for_two = MoneyField(default=Decimal("300.00"))
    # status
    status = models.CharField(max_length=20, choices=RestaurantStatus.choices, default=RestaurantStatus.PENDING,
                              db_index=True)
    rejection_reason = models.TextField(blank=True, default="")
    approved_at = models.DateTimeField(null=True, blank=True)
    is_open = models.BooleanField(default=False, help_text="Manual open/closed toggle by the owner")
    is_accepting_orders = models.BooleanField(default=True)
    temporarily_closed_until = models.DateTimeField(null=True, blank=True)
    is_featured = models.BooleanField(default=False)
    is_promoted = models.BooleanField(default=False)
    # denormalised stats
    rating_avg = models.DecimalField(max_digits=3, decimal_places=2, default=Decimal("0.00"))
    rating_count = models.PositiveIntegerField(default=0)
    total_orders = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ["-is_promoted", "-rating_avg", "name"]
        indexes = [
            models.Index(fields=["status", "is_open"]),
            models.Index(fields=["city", "status"]),
        ]

    def __str__(self):
        return self.name

    def save(self, *args, **kwargs):
        if not self.slug:
            base = slugify(self.name)[:120] or "restaurant"
            slug, i = base, 1
            while Restaurant.all_objects.filter(slug=slug).exclude(pk=self.pk).exists():
                i += 1
                slug = f"{base}-{i}"
            self.slug = slug
        super().save(*args, **kwargs)

    @property
    def latitude(self):
        return self.location.y

    @property
    def longitude(self):
        return self.location.x

    @property
    def is_approved(self):
        return self.status == RestaurantStatus.APPROVED

    @property
    def effective_commission_percent(self) -> Decimal:
        if self.commission_percent is not None:
            return self.commission_percent
        from apps.core.models import PlatformSettings

        return PlatformSettings.load().default_commission_percent

    def is_within_hours(self, at=None) -> bool:
        """Uses prefetched `hours`/`holidays` when available (list views prefetch them to avoid N+1 queries)."""
        at = timezone.localtime(at or timezone.now())
        prefetched = getattr(self, "_prefetched_objects_cache", {})
        if "holidays" in prefetched:
            if any(h.date == at.date() for h in self.holidays.all()):
                return False
        elif self.holidays.filter(date=at.date()).exists():
            return False
        if "hours" in prefetched:
            hours = [h for h in self.hours.all() if h.weekday == at.weekday() and not h.is_closed]
        else:
            hours = self.hours.filter(weekday=at.weekday(), is_closed=False)
        t = at.time()
        for h in hours:
            if h.open_time <= h.close_time:
                if h.open_time <= t <= h.close_time:
                    return True
            else:  # overnight (e.g. 18:00 - 02:00)
                if t >= h.open_time or t <= h.close_time:
                    return True
        return False

    def can_accept_orders_now(self) -> bool:
        if not self.is_approved or not self.is_open or not self.is_accepting_orders:
            return False
        if self.temporarily_closed_until and self.temporarily_closed_until > timezone.now():
            return False
        return self.is_within_hours()


class RestaurantHours(models.Model):
    WEEKDAYS = [(0, "Monday"), (1, "Tuesday"), (2, "Wednesday"), (3, "Thursday"), (4, "Friday"),
                (5, "Saturday"), (6, "Sunday")]
    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE, related_name="hours")
    weekday = models.PositiveSmallIntegerField(choices=WEEKDAYS)
    open_time = models.TimeField()
    close_time = models.TimeField()
    is_closed = models.BooleanField(default=False)

    class Meta:
        ordering = ["weekday", "open_time"]
        verbose_name_plural = "Restaurant hours"
        constraints = [models.UniqueConstraint(fields=["restaurant", "weekday", "open_time"],
                                               name="uniq_restaurant_hours_slot")]


class RestaurantHoliday(models.Model):
    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE, related_name="holidays")
    date = models.DateField()
    reason = models.CharField(max_length=120, blank=True, default="")

    class Meta:
        unique_together = ("restaurant", "date")


class DocumentType(models.TextChoices):
    FSSAI = "fssai", "FSSAI licence"
    GST = "gst", "GST certificate"
    PAN = "pan", "PAN card"
    BANK_PROOF = "bank_proof", "Cancelled cheque / bank proof"
    OWNER_ID = "owner_id", "Owner ID proof"
    MENU = "menu", "Menu card"
    OTHER = "other", "Other"


class DocumentStatus(models.TextChoices):
    PENDING = "pending", "Pending"
    VERIFIED = "verified", "Verified"
    REJECTED = "rejected", "Rejected"


class RestaurantDocument(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE, related_name="documents")
    doc_type = models.CharField(max_length=20, choices=DocumentType.choices)
    file = models.FileField(upload_to="restaurants/documents/%Y/%m/", validators=[validate_document_upload])
    number = models.CharField(max_length=60, blank=True, default="")
    status = models.CharField(max_length=10, choices=DocumentStatus.choices, default=DocumentStatus.PENDING)
    rejection_reason = models.CharField(max_length=255, blank=True, default="")
    verified_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
                                    related_name="+")
    verified_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]


class Banner(TimeStampedModel):
    """Promotional banners shown on the customer home screen."""

    title = models.CharField(max_length=80)
    subtitle = models.CharField(max_length=140, blank=True, default="")
    image = models.ImageField(upload_to="banners/", validators=[validate_image_upload])
    restaurant = models.ForeignKey(Restaurant, null=True, blank=True, on_delete=models.CASCADE, related_name="banners")
    coupon_code = models.CharField(max_length=30, blank=True, default="")
    deep_link = models.CharField(max_length=255, blank=True, default="")
    city = models.CharField(max_length=80, blank=True, default="", help_text="Blank = all cities")
    sort_order = models.PositiveSmallIntegerField(default=0)
    is_active = models.BooleanField(default=True)
    starts_at = models.DateTimeField(null=True, blank=True)
    ends_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["sort_order", "-created_at"]

    def __str__(self):
        return self.title
