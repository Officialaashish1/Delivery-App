from django.contrib import admin

from .models import ChatMessage


@admin.register(ChatMessage)
class ChatAdmin(admin.ModelAdmin):
    list_display = ("order", "sender_role", "text", "created_at")
