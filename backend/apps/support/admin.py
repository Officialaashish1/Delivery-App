from django.contrib import admin

from .models import FAQ, SupportTicket, TicketMessage


class MessageInline(admin.TabularInline):
    model = TicketMessage
    extra = 0


@admin.register(SupportTicket)
class TicketAdmin(admin.ModelAdmin):
    list_display = ("ticket_number", "user", "category", "subject", "status", "priority", "created_at")
    list_filter = ("status", "priority", "category")
    search_fields = ("ticket_number", "user__phone", "subject")
    inlines = [MessageInline]


admin.site.register(FAQ)
