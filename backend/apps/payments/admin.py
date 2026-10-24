from django.contrib import admin

from .models import Payment, Refund, WebhookEvent


@admin.register(Payment)
class PaymentAdmin(admin.ModelAdmin):
    list_display = ("order", "method", "amount", "status", "provider_payment_id", "captured_at")
    list_filter = ("status", "method", "provider")
    search_fields = ("order__order_number", "provider_payment_id", "provider_order_id")


@admin.register(Refund)
class RefundAdmin(admin.ModelAdmin):
    list_display = ("order", "amount", "status", "reason", "processed_at")
    list_filter = ("status",)


@admin.register(WebhookEvent)
class WebhookAdmin(admin.ModelAdmin):
    list_display = ("provider", "event_type", "event_id", "processed", "received_at")
    list_filter = ("provider", "processed", "event_type")
