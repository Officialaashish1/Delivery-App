from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as BaseUserAdmin
from django.contrib.gis.admin import GISModelAdmin

from .models import Address, CustomerProfile, Device, OTPRequest, User


@admin.register(User)
class UserAdmin(BaseUserAdmin):
    ordering = ("-created_at",)
    list_display = ("phone", "full_name", "role", "is_phone_verified", "is_suspended", "is_active", "created_at")
    list_filter = ("role", "is_suspended", "is_active", "is_staff")
    search_fields = ("phone", "full_name", "email")
    fieldsets = (
        (None, {"fields": ("phone", "password")}),
        ("Profile", {"fields": ("full_name", "email", "role")}),
        ("Status", {"fields": ("is_active", "is_phone_verified", "is_email_verified", "is_suspended",
                               "suspension_reason", "token_version")}),
        ("Permissions", {"fields": ("is_staff", "is_superuser", "groups", "user_permissions")}),
        ("Dates", {"fields": ("last_login", "last_seen_at")}),
    )
    add_fieldsets = ((None, {"classes": ("wide",), "fields": ("phone", "role", "password1", "password2")}),)
    readonly_fields = ("last_login", "last_seen_at")


@admin.register(CustomerProfile)
class CustomerProfileAdmin(admin.ModelAdmin):
    list_display = ("user", "referral_code", "total_orders", "created_at")
    search_fields = ("user__phone", "user__full_name", "referral_code")


@admin.register(Address)
class AddressAdmin(GISModelAdmin):
    list_display = ("user", "label", "address_line1", "city", "is_default")
    search_fields = ("user__phone", "address_line1", "city")


@admin.register(Device)
class DeviceAdmin(admin.ModelAdmin):
    list_display = ("user", "app", "platform", "app_version", "is_active", "last_used_at")
    list_filter = ("app", "platform", "is_active")


@admin.register(OTPRequest)
class OTPAdmin(admin.ModelAdmin):
    list_display = ("phone", "purpose", "role", "attempts", "is_used", "expires_at", "created_at")
    readonly_fields = ("code_hash",)
