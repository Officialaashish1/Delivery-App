import uuid
from decimal import Decimal

from django.conf import settings
from django.contrib.gis.db import models as gis_models
from django.db import models
from django.utils import timezone

from apps.core.models import MoneyField, TimeStampedModel
from apps.core.validators import validate_document_upload, validate_image_upload


class RiderStatus(models.TextChoices):
    PENDING = "pending", "Pending verification"
    UNDER_REVIEW = "under_review", "Under review"
    APPROVED = "approved", "Approved"
    REJECTED = "rejected", "Rejected"
    SUSPENDED = "suspended", "Suspended"


class VehicleType(models.TextChoices):
    BIKE = "bike", "Motorcycle"
    SCOOTER = "scooter", "Scooter"
    BICYCLE = "bicycle", "Bicycle"
    EV = "ev", "Electric two-wheeler"


class RiderProfile(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="rider_profile")
    full_name = models.CharField(max_length=120, blank=True, default="")
    photo = models.ImageField(upload_to="riders/photos/", blank=True, null=True, validators=[validate_image_upload])
    email = models.EmailField(blank=True, default="")
    date_of_birth = models.DateField(null=True, blank=True)
    address = models.CharField(max_length=512, blank=True, default="")
    city = models.CharField(max_length=80, blank=True, default="", db_index=True)
    # vehicle & licence
    vehicle_type = models.CharField(max_length=10, choices=VehicleType.choices, default=VehicleType.BIKE)
    vehicle_number = models.CharField(max_length=16, blank=True, default="")
    driving_licence_number = models.CharField(max_length=24, blank=True, default="")
    driving_licence_expiry = models.DateField(null=True, blank=True)
    # identity
    aadhaar_last4 = models.CharField(max_length=4, blank=True, default="")
    pan_number = models.CharField(max_length=12, blank=True, default="")
    # bank
    bank_account_name = models.CharField(max_length=120, blank=True, default="")
    bank_account_number = models.CharField(max_length=24, blank=True, default="")
    bank_ifsc = models.CharField(max_length=12, blank=True, default="")
    upi_id = models.CharField(max_length=80, blank=True, default="")
    # emergency
    emergency_contact_name = models.CharField(max_length=120, blank=True, default="")
    emergency_contact_phone = models.CharField(max_length=16, blank=True, default="")
    # status
    status = models.CharField(max_length=14, choices=RiderStatus.choices, default=RiderStatus.PENDING, db_index=True)
    rejection_reason = models.TextField(blank=True, default="")
    approved_at = models.DateTimeField(null=True, blank=True)
    is_online = models.BooleanField(default=False, db_index=True)
    is_on_delivery = models.BooleanField(default=False)
    last_online_at = models.DateTimeField(null=True, blank=True)
    # live location (latest; history lives in RiderLocation / Redis)
    current_location = gis_models.PointField(geography=True, srid=4326, null=True, blank=True)
    location_updated_at = models.DateTimeField(null=True, blank=True)
    heading = models.FloatField(default=0)
    speed_kmph = models.FloatField(default=0)
    # stats
    rating_avg = models.DecimalField(max_digits=3, decimal_places=2, default=Decimal("0.00"))
    rating_count = models.PositiveIntegerField(default=0)
    total_deliveries = models.PositiveIntegerField(default=0)
    total_rejections = models.PositiveIntegerField(default=0)
    wallet_balance = MoneyField(help_text="Unsettled earnings")
    cod_cash_in_hand = MoneyField(help_text="COD collected and not yet deposited")

    class Meta:
        indexes = [models.Index(fields=["status", "is_online", "is_on_delivery"])]

    def __str__(self):
        return f"Rider {self.full_name or self.user.phone}"

    @property
    def is_approved(self):
        return self.status == RiderStatus.APPROVED

    @property
    def can_take_orders(self):
        return self.is_approved and self.is_online and not self.user.is_suspended

    def set_location(self, lat, lng, heading=0.0, speed=0.0, at=None):
        from django.contrib.gis.geos import Point

        self.current_location = Point(float(lng), float(lat), srid=4326)
        self.heading = heading
        self.speed_kmph = speed
        self.location_updated_at = at or timezone.now()
        self.save(update_fields=["current_location", "heading", "speed_kmph", "location_updated_at"])


class RiderDocumentType(models.TextChoices):
    DRIVING_LICENCE = "driving_licence", "Driving licence"
    AADHAAR = "aadhaar", "Aadhaar"
    PAN = "pan", "PAN card"
    VEHICLE_RC = "vehicle_rc", "Vehicle RC"
    INSURANCE = "insurance", "Vehicle insurance"
    BANK_PROOF = "bank_proof", "Bank proof"
    PHOTO = "photo", "Profile photo"
    OTHER = "other", "Other"


class RiderDocument(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    rider = models.ForeignKey(RiderProfile, on_delete=models.CASCADE, related_name="documents")
    doc_type = models.CharField(max_length=20, choices=RiderDocumentType.choices)
    file = models.FileField(upload_to="riders/documents/%Y/%m/", validators=[validate_document_upload])
    number = models.CharField(max_length=60, blank=True, default="")
    status = models.CharField(max_length=10, default="pending",
                              choices=[("pending", "Pending"), ("verified", "Verified"), ("rejected", "Rejected")])
    rejection_reason = models.CharField(max_length=255, blank=True, default="")
    verified_at = models.DateTimeField(null=True, blank=True)
    verified_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
                                    related_name="+")

    class Meta:
        ordering = ["-created_at"]


class RiderLocation(models.Model):
    """Persisted GPS history (sampled). Hot path uses Redis; this is for audits, disputes and analytics."""

    id = models.BigAutoField(primary_key=True)
    rider = models.ForeignKey(RiderProfile, on_delete=models.CASCADE, related_name="locations")
    order = models.ForeignKey("orders.Order", null=True, blank=True, on_delete=models.SET_NULL,
                              related_name="rider_locations")
    location = gis_models.PointField(geography=True, srid=4326)
    accuracy_m = models.FloatField(default=0)
    speed_kmph = models.FloatField(default=0)
    bearing = models.FloatField(default=0)
    recorded_at = models.DateTimeField(db_index=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        indexes = [models.Index(fields=["rider", "-recorded_at"]), models.Index(fields=["order", "recorded_at"])]


class EarningType(models.TextChoices):
    DELIVERY = "delivery", "Delivery pay"
    DISTANCE = "distance", "Distance pay"
    INCENTIVE = "incentive", "Incentive"
    BONUS = "bonus", "Bonus"
    TIP = "tip", "Tip"
    PENALTY = "penalty", "Penalty"
    ADJUSTMENT = "adjustment", "Manual adjustment"
    COD_DEPOSIT = "cod_deposit", "COD cash deposited"


class RiderEarning(TimeStampedModel):
    """Ledger of rider earnings. Positive = credit to rider. Immutable once created."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    rider = models.ForeignKey(RiderProfile, on_delete=models.PROTECT, related_name="earnings")
    order = models.ForeignKey("orders.Order", null=True, blank=True, on_delete=models.SET_NULL,
                              related_name="rider_earnings")
    earning_type = models.CharField(max_length=12, choices=EarningType.choices)
    amount = MoneyField(allow_negative=True)
    description = models.CharField(max_length=255, blank=True, default="")
    distance_km = models.DecimalField(max_digits=6, decimal_places=2, default=Decimal("0.00"))
    payout = models.ForeignKey("RiderPayout", null=True, blank=True, on_delete=models.SET_NULL,
                               related_name="earnings")
    is_settled = models.BooleanField(default=False, db_index=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [models.Index(fields=["rider", "is_settled"]), models.Index(fields=["rider", "-created_at"])]


class PayoutStatus(models.TextChoices):
    REQUESTED = "requested", "Requested"
    PROCESSING = "processing", "Processing"
    PAID = "paid", "Paid"
    FAILED = "failed", "Failed"
    REJECTED = "rejected", "Rejected"


class RiderPayout(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    rider = models.ForeignKey(RiderProfile, on_delete=models.PROTECT, related_name="payouts")
    amount = MoneyField()
    status = models.CharField(max_length=12, choices=PayoutStatus.choices, default=PayoutStatus.REQUESTED)
    period_start = models.DateField(null=True, blank=True)
    period_end = models.DateField(null=True, blank=True)
    reference = models.CharField(max_length=80, blank=True, default="", help_text="Bank/UPI transaction reference")
    note = models.CharField(max_length=255, blank=True, default="")
    processed_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
                                     related_name="+")
    processed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]


class IncentiveRule(models.Model):
    """Simple incentive: complete N deliveries in a day → bonus amount."""

    name = models.CharField(max_length=80)
    city = models.CharField(max_length=80, blank=True, default="")
    deliveries_required = models.PositiveSmallIntegerField()
    bonus_amount = MoneyField()
    is_active = models.BooleanField(default=True)
    starts_on = models.DateField(null=True, blank=True)
    ends_on = models.DateField(null=True, blank=True)

    def __str__(self):
        return self.name
