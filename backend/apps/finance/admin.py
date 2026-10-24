from django.contrib import admin

from .models import PlatformLedger, RestaurantSettlement, SettlementLine


@admin.register(RestaurantSettlement)
class SettlementAdmin(admin.ModelAdmin):
    list_display = ("restaurant", "period_start", "period_end", "order_count", "net_payable", "status", "processed_at")
    list_filter = ("status",)


@admin.register(SettlementLine)
class SettlementLineAdmin(admin.ModelAdmin):
    list_display = ("order", "restaurant", "gross", "commission", "net", "is_cod", "delivered_at")


@admin.register(PlatformLedger)
class LedgerAdmin(admin.ModelAdmin):
    list_display = ("date", "orders", "gross_order_value", "commission_revenue", "net_revenue")
