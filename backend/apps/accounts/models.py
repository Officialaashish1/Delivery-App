import secrets
import uuid

from django.conf import settings
from django.contrib.auth.base_user import AbstractBaseUser, BaseUserManager
from django.contrib.auth.models import PermissionsMixin
from django.contrib.gis.db import models as gis_models
from django.core.validators import RegexValidator
from django.db import models
from django.utils import timezone

from apps.core.models import SoftDeleteModel, TimeStampedModel
from apps.core.validators import validate_image_upload

phone_validator = RegexValidator(r"^\+[1-9]\d{7,14}$", "Phone must be in E.164 format, e.g. +919876543210")


class UserRole(models.TextChoices):
    CUSTOMER = "customer", "Customer"
    RIDER = "rider", "Rider"
    PARTNER = "partner", "Restaurant partner"
    ADMIN = "admin", "Admin"


class UserManager(BaseUserManager):
    def create_user(self, phone, password=None, role=UserRole.CUSTOMER, **extra):
        if not phone:
            raise ValueError("Phone is required")
        if extra.get("email"):
            extra["email"] = self.normalize_email(extra["email"])
        user = self.model(phone=phone, role=role, **extra)
        if password:
            user.set_password(password)
        else:
            user.set_unusable_password()
        user.save(using=self._db)
        return user

    def create_superuser(self, phone, password, **extra):
        extra.setdefault("is_staff", True)
        extra.setdefault("is_superuser", True)
        extra.setdefault("is_phone_verified", True)
        return self.create_user(phone, password, role=UserRole.ADMIN, **extra)


class User(AbstractBaseUser, PermissionsMixin, TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    phone = models.CharField(max_length=16, unique=True, validators=[phone_validator])
    email = models.EmailField(blank=True, null=True, unique=True)
    full_name = models.CharField(max_length=120, blank=True, default="")
    role = models.CharField(max_length=16, choices=UserRole.choices, default=UserRole.CUSTOMER, db_index=True)
    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=False)
    is_phone_verified = models.BooleanField(default=False)
    is_email_verified = models.BooleanField(default=False)
    is_suspended = models.BooleanField(default=False)
    suspension_reason = models.CharField(max_length=255, blank=True, default="")
    token_version = models.PositiveIntegerField(default=1, help_text="Bump to revoke all issued JWTs")
    last_seen_at = models.DateTimeField(null=True, blank=True)

    USERNAME_FIELD = "phone"
    REQUIRED_FIELDS: list[str] = []

    objects = UserManager()

    class Meta:
        indexes = [models.Index(fields=["role", "is_active"])]

    def __str__(self):
        return f"{self.full_name or self.phone} ({self.role})"

    @property
    def can_login(self):
        return self.is_active and not self.is_suspended

    def revoke_all_tokens(self):
        self.token_version += 1
        self.save(update_fields=["token_version"])


class OTPPurpose(models.TextChoices):
    LOGIN = "login", "Login / register"
    PASSWORD_RESET = "password_reset", "Password reset"
    PHONE_CHANGE = "phone_change", "Phone change"


class OTPRequest(models.Model):
    """One-time passcodes. The code is stored hashed; never returned by the API."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    phone = models.CharField(max_length=16, db_index=True)
    purpose = models.CharField(max_length=20, choices=OTPPurpose.choices, default=OTPPurpose.LOGIN)
    role = models.CharField(max_length=16, choices=UserRole.choices, default=UserRole.CUSTOMER)
    code_hash = models.CharField(max_length=128)
    attempts = models.PositiveSmallIntegerField(default=0)
    is_used = models.BooleanField(default=False)
    expires_at = models.DateTimeField()
    created_at = models.DateTimeField(auto_now_add=True)
    ip_address = models.GenericIPAddressField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [models.Index(fields=["phone", "purpose", "-created_at"])]

    @property
    def is_expired(self):
        return timezone.now() >= self.expires_at


class CustomerProfile(TimeStampedModel):
    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="customer_profile")
    avatar = models.ImageField(upload_to="avatars/%Y/%m/", blank=True, null=True, validators=[validate_image_upload])
    date_of_birth = models.DateField(null=True, blank=True)
    referral_code = models.CharField(max_length=12, unique=True, blank=True, null=True)
    referred_by = models.ForeignKey("self", null=True, blank=True, on_delete=models.SET_NULL, related_name="referrals")
    is_veg_only = models.BooleanField(default=False)
    total_orders = models.PositiveIntegerField(default=0)
    first_order_at = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return f"Customer {self.user.phone}"

    def save(self, *args, **kwargs):
        if not self.referral_code:
            self.referral_code = "LR" + secrets.token_hex(4).upper()
        super().save(*args, **kwargs)

    @property
    def is_first_order(self):
        return self.total_orders == 0


class AddressLabel(models.TextChoices):
    HOME = "home", "Home"
    WORK = "work", "Work"
    OTHER = "other", "Other"


class Address(SoftDeleteModel, TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="addresses")
    label = models.CharField(max_length=10, choices=AddressLabel.choices, default=AddressLabel.HOME)
    name = models.CharField(max_length=80, blank=True, default="", help_text="Custom label e.g. 'Mom's place'")
    contact_name = models.CharField(max_length=120, blank=True, default="")
    contact_phone = models.CharField(max_length=16, blank=True, default="", validators=[phone_validator])
    address_line1 = models.CharField(max_length=255, help_text="Flat / house / building")
    address_line2 = models.CharField(max_length=255, blank=True, default="", help_text="Street / area")
    landmark = models.CharField(max_length=255, blank=True, default="")
    city = models.CharField(max_length=80)
    state = models.CharField(max_length=80, blank=True, default="")
    postal_code = models.CharField(max_length=10, blank=True, default="")
    location = gis_models.PointField(geography=True, srid=4326)
    is_default = models.BooleanField(default=False)
    delivery_instructions = models.CharField(max_length=255, blank=True, default="")

    class Meta:
        ordering = ["-is_default", "-updated_at"]
        indexes = [models.Index(fields=["user", "is_default"])]

    def __str__(self):
        return f"{self.get_label_display()}: {self.address_line1}, {self.city}"

    @property
    def latitude(self):
        return self.location.y

    @property
    def longitude(self):
        return self.location.x

    @property
    def formatted(self):
        parts = [self.address_line1, self.address_line2, self.landmark, self.city, self.postal_code]
        return ", ".join(p for p in parts if p)

    def save(self, *args, **kwargs):
        super().save(*args, **kwargs)
        if self.is_default:
            Address.objects.filter(user=self.user, is_default=True).exclude(pk=self.pk).update(is_default=False)


class DevicePlatform(models.TextChoices):
    ANDROID = "android", "Android"
    IOS = "ios", "iOS"
    WEB = "web", "Web"


class AppKind(models.TextChoices):
    CUSTOMER = "customer", "Customer app"
    RIDER = "rider", "Rider app"
    PARTNER = "partner", "Partner app"
    ADMIN = "admin", "Admin"


class Device(TimeStampedModel):
    """Push notification registration (FCM)."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="devices")
    fcm_token = models.CharField(max_length=512, unique=True)
    platform = models.CharField(max_length=10, choices=DevicePlatform.choices, default=DevicePlatform.ANDROID)
    app = models.CharField(max_length=10, choices=AppKind.choices, default=AppKind.CUSTOMER)
    device_id = models.CharField(max_length=128, blank=True, default="")
    app_version = models.CharField(max_length=20, blank=True, default="")
    is_active = models.BooleanField(default=True)
    last_used_at = models.DateTimeField(auto_now=True)

    class Meta:
        indexes = [models.Index(fields=["user", "is_active"])]
