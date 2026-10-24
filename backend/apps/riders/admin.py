from django.contrib import admin
from django.contrib.gis.admin import GISModelAdmin

from .models import IncentiveRule, RiderDocument, RiderEarning, RiderLocation, RiderPayout, RiderProfile


class RiderDocInline(admin.TabularInline):
    model = RiderDocument
    extra = 0


@admin.register(RiderProfile)
class RiderAdmin(GISModelAdmin):
    list_display = ("full_name", "user", "city", "vehicle_type", "status", "is_online", "is_on_delivery",
                    "rating_avg", "total_deliveries", "wallet_balance")
    list_filter = ("status", "is_online", "vehicle_type", "city")
    search_fields = ("full_name", "user__phone", "vehicle_number")
    inlines = [RiderDocInline]


@admin.register(RiderEarning)
class RiderEarningAdmin(admin.ModelAdmin):
    list_display = ("rider", "earning_type", "amount", "order", "is_settled", "created_at")
    list_filter = ("earning_type", "is_settled")


@admin.register(RiderPayout)
class RiderPayoutAdmin(admin.ModelAdmin):
    list_display = ("rider", "amount", "status", "reference", "processed_at", "created_at")
    list_filter = ("status",)


@admin.register(RiderLocation)
class RiderLocationAdmin(admin.ModelAdmin):
    list_display = ("rider", "order", "recorded_at", "speed_kmph")


admin.site.register(IncentiveRule)
