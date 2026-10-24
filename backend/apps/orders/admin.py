from django.contrib import admin

from .models import Order, OrderItem, OrderStatusHistory, RiderAssignment


class ItemInline(admin.TabularInline):
    model = OrderItem
    extra = 0
    readonly_fields = ("name", "variant_name", "addon_names", "quantity", "unit_price", "line_total")


class HistoryInline(admin.TabularInline):
    model = OrderStatusHistory
    extra = 0
    readonly_fields = ("from_status", "to_status", "changed_by", "actor_role", "note", "created_at")


@admin.register(Order)
class OrderAdmin(admin.ModelAdmin):
    list_display = ("order_number", "customer", "restaurant_name", "rider", "status", "total_amount",
                    "payment_method", "is_paid", "placed_at")
    list_filter = ("status", "payment_method", "is_paid")
    search_fields = ("order_number", "customer__phone", "restaurant_name")
    readonly_fields = ("pickup_otp", "delivery_otp")
    inlines = [ItemInline, HistoryInline]
    date_hierarchy = "created_at"


@admin.register(RiderAssignment)
class AssignmentAdmin(admin.ModelAdmin):
    list_display = ("order", "rider", "status", "offered_at", "expires_at", "responded_at")
    list_filter = ("status",)
