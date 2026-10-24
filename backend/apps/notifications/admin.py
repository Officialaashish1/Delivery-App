from django.contrib import admin

from .models import Notification, PromoCampaign


@admin.register(Notification)
class NotificationAdmin(admin.ModelAdmin):
    list_display = ("user", "category", "title", "is_read", "push_sent", "created_at")
    list_filter = ("category", "is_read", "push_sent")
    search_fields = ("user__phone", "title")


@admin.register(PromoCampaign)
class CampaignAdmin(admin.ModelAdmin):
    list_display = ("title", "audience", "city", "sent_count", "sent_at")
