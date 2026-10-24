import uuid
from decimal import Decimal

from django.db import models

from apps.core.models import MoneyField, SoftDeleteModel, TimeStampedModel
from apps.core.validators import validate_image_upload
from apps.restaurants.models import Restaurant


class FoodType(models.TextChoices):
    VEG = "veg", "Vegetarian"
    NON_VEG = "non_veg", "Non-vegetarian"
    EGG = "egg", "Contains egg"


class MenuCategory(TimeStampedModel):
    """A section on a restaurant's menu (Starters, Main course, Breads, ...)."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE, related_name="menu_categories")
    name = models.CharField(max_length=80)
    description = models.CharField(max_length=255, blank=True, default="")
    sort_order = models.PositiveSmallIntegerField(default=0)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["sort_order", "name"]
        verbose_name_plural = "Menu categories"
        constraints = [models.UniqueConstraint(fields=["restaurant", "name"], name="uniq_menu_category_name")]

    def __str__(self):
        return f"{self.restaurant.name} / {self.name}"


class MenuItem(SoftDeleteModel, TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE, related_name="menu_items")
    category = models.ForeignKey(MenuCategory, on_delete=models.PROTECT, related_name="items")
    name = models.CharField(max_length=120, db_index=True)
    description = models.TextField(blank=True, default="")
    image = models.ImageField(upload_to="menu/%Y/%m/", blank=True, null=True, validators=[validate_image_upload])
    food_type = models.CharField(max_length=10, choices=FoodType.choices, default=FoodType.VEG)
    price = MoneyField(help_text="Base price (used when the item has no variants)")
    discounted_price = MoneyField(null=True, blank=True, default=None)
    packaging_charge = MoneyField(default=Decimal("0.00"))
    preparation_minutes = models.PositiveSmallIntegerField(default=15)
    is_available = models.BooleanField(default=True)
    stock_quantity = models.PositiveIntegerField(null=True, blank=True, help_text="Blank = unlimited")
    is_recommended = models.BooleanField(default=False)
    is_bestseller = models.BooleanField(default=False)
    spice_level = models.PositiveSmallIntegerField(default=0)
    calories = models.PositiveIntegerField(null=True, blank=True)
    tags = models.JSONField(default=list, blank=True)
    sort_order = models.PositiveSmallIntegerField(default=0)
    order_count = models.PositiveIntegerField(default=0)
    rating_avg = models.DecimalField(max_digits=3, decimal_places=2, default=Decimal("0.00"))
    rating_count = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ["category__sort_order", "sort_order", "name"]
        indexes = [models.Index(fields=["restaurant", "is_available"]), models.Index(fields=["name"])]

    def __str__(self):
        return f"{self.name} ({self.restaurant.name})"

    @property
    def effective_price(self) -> Decimal:
        if self.discounted_price is not None and self.discounted_price < self.price:
            return self.discounted_price
        return self.price

    @property
    def in_stock(self) -> bool:
        return self.is_available and (self.stock_quantity is None or self.stock_quantity > 0)

    @property
    def has_variants(self):
        return self.variant_groups.exists()


class VariantGroup(models.Model):
    """Choose exactly one option, e.g. Size: Regular / Medium / Large."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    item = models.ForeignKey(MenuItem, on_delete=models.CASCADE, related_name="variant_groups")
    name = models.CharField(max_length=60)
    is_required = models.BooleanField(default=True)
    sort_order = models.PositiveSmallIntegerField(default=0)

    class Meta:
        ordering = ["sort_order"]


class MenuVariant(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    group = models.ForeignKey(VariantGroup, on_delete=models.CASCADE, related_name="options")
    name = models.CharField(max_length=60)
    price = MoneyField(help_text="Absolute price for this variant (replaces base price)")
    is_available = models.BooleanField(default=True)
    is_default = models.BooleanField(default=False)
    sort_order = models.PositiveSmallIntegerField(default=0)

    class Meta:
        ordering = ["sort_order"]

    def __str__(self):
        return f"{self.group.name}: {self.name}"


class AddonGroup(models.Model):
    """Choose 0..max options, e.g. Extra toppings."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    item = models.ForeignKey(MenuItem, on_delete=models.CASCADE, related_name="addon_groups")
    name = models.CharField(max_length=60)
    min_select = models.PositiveSmallIntegerField(default=0)
    max_select = models.PositiveSmallIntegerField(default=5)
    sort_order = models.PositiveSmallIntegerField(default=0)

    class Meta:
        ordering = ["sort_order"]


class MenuAddon(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    group = models.ForeignKey(AddonGroup, on_delete=models.CASCADE, related_name="options")
    name = models.CharField(max_length=60)
    price = MoneyField(help_text="Added on top of item/variant price")
    food_type = models.CharField(max_length=10, choices=FoodType.choices, default=FoodType.VEG)
    is_available = models.BooleanField(default=True)
    sort_order = models.PositiveSmallIntegerField(default=0)

    class Meta:
        ordering = ["sort_order"]

    def __str__(self):
        return f"{self.group.name}: {self.name}"
