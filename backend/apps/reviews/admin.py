from django.contrib import admin

from .models import Review


@admin.register(Review)
class ReviewAdmin(admin.ModelAdmin):
    list_display = ("order", "restaurant", "restaurant_rating", "rider", "rider_rating", "is_visible", "created_at")
    list_filter = ("restaurant_rating", "is_visible")
