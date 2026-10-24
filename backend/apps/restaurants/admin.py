from django.contrib import admin
from django.contrib.gis.admin import GISModelAdmin

from .models import (
    Banner,
    Cuisine,
    Restaurant,
    RestaurantCategory,
    RestaurantDocument,
    RestaurantHoliday,
    RestaurantHours,
)


class HoursInline(admin.TabularInline):
    model = RestaurantHours
    extra = 0


class DocumentInline(admin.TabularInline):
    model = RestaurantDocument
    extra = 0
    readonly_fields = ("file", "doc_type", "number")


@admin.register(Restaurant)
class RestaurantAdmin(GISModelAdmin):
    list_display = ("name", "city", "owner", "status", "is_open", "rating_avg", "total_orders", "created_at")
    list_filter = ("status", "is_open", "city", "is_pure_veg", "restaurant_type")
    search_fields = ("name", "owner__phone", "phone", "fssai_number")
    inlines = [HoursInline, DocumentInline]
    filter_horizontal = ("cuisines", "categories")
    readonly_fields = ("slug", "rating_avg", "rating_count", "total_orders")


admin.site.register(Cuisine)
admin.site.register(RestaurantCategory)
admin.site.register(RestaurantHoliday)
admin.site.register(Banner)
