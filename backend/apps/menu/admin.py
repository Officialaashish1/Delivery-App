from django.contrib import admin

from .models import AddonGroup, MenuAddon, MenuCategory, MenuItem, MenuVariant, VariantGroup


class VariantInline(admin.TabularInline):
    model = MenuVariant
    extra = 0


class AddonInline(admin.TabularInline):
    model = MenuAddon
    extra = 0


@admin.register(VariantGroup)
class VariantGroupAdmin(admin.ModelAdmin):
    inlines = [VariantInline]


@admin.register(AddonGroup)
class AddonGroupAdmin(admin.ModelAdmin):
    inlines = [AddonInline]


@admin.register(MenuItem)
class MenuItemAdmin(admin.ModelAdmin):
    list_display = ("name", "restaurant", "category", "food_type", "price", "discounted_price", "is_available")
    list_filter = ("food_type", "is_available", "restaurant")
    search_fields = ("name", "restaurant__name")


admin.site.register(MenuCategory)
