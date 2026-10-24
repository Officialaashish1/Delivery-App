from django.contrib import admin

from .models import Coupon, CouponUsage


@admin.register(Coupon)
class CouponAdmin(admin.ModelAdmin):
    list_display = ("code", "title", "discount_type", "value", "min_order_amount", "used_count", "is_active",
                    "expires_at")
    list_filter = ("discount_type", "is_active", "first_order_only")
    search_fields = ("code", "title")


@admin.register(CouponUsage)
class CouponUsageAdmin(admin.ModelAdmin):
    list_display = ("coupon", "user", "order", "discount_amount", "created_at")
