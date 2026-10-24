import uuid

from django.conf import settings
from django.db import models

from apps.core.models import TimeStampedModel
from apps.menu.models import MenuAddon, MenuItem, MenuVariant
from apps.restaurants.models import Restaurant


class Cart(TimeStampedModel):
    """Server-side cart. One cart per customer, bound to a single restaurant."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="cart")
    restaurant = models.ForeignKey(Restaurant, null=True, blank=True, on_delete=models.SET_NULL)
    coupon_code = models.CharField(max_length=30, blank=True, default="")
    delivery_address = models.ForeignKey("accounts.Address", null=True, blank=True, on_delete=models.SET_NULL)
    special_instructions = models.CharField(max_length=255, blank=True, default="")

    def clear(self):
        self.items.all().delete()
        self.restaurant = None
        self.coupon_code = ""
        self.special_instructions = ""
        self.save(update_fields=["restaurant", "coupon_code", "special_instructions"])

    @property
    def item_count(self):
        return sum(i.quantity for i in self.items.all())


class CartItem(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    cart = models.ForeignKey(Cart, on_delete=models.CASCADE, related_name="items")
    menu_item = models.ForeignKey(MenuItem, on_delete=models.CASCADE)
    variant = models.ForeignKey(MenuVariant, null=True, blank=True, on_delete=models.CASCADE)
    addons = models.ManyToManyField(MenuAddon, blank=True)
    quantity = models.PositiveSmallIntegerField(default=1)
    special_instructions = models.CharField(max_length=255, blank=True, default="")

    class Meta:
        ordering = ["created_at"]

    def same_config_as(self, variant, addon_ids: set) -> bool:
        return (self.variant_id == (variant.id if variant else None)
                and set(self.addons.values_list("id", flat=True)) == set(addon_ids))
