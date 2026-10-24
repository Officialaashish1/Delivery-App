from rest_framework import serializers

from apps.accounts.serializers import AddressSerializer
from apps.menu.serializers import MenuAddonSerializer, MenuVariantSerializer
from apps.restaurants.serializers import RestaurantListSerializer

from .models import Cart, CartItem


class AddToCartSerializer(serializers.Serializer):
    menu_item_id = serializers.UUIDField()
    quantity = serializers.IntegerField(min_value=1, max_value=20, default=1)
    variant_id = serializers.UUIDField(required=False, allow_null=True)
    addon_ids = serializers.ListField(child=serializers.UUIDField(), required=False, default=list)
    special_instructions = serializers.CharField(max_length=255, required=False, allow_blank=True, default="")
    replace_cart = serializers.BooleanField(default=False)


class UpdateCartItemSerializer(serializers.Serializer):
    quantity = serializers.IntegerField(min_value=0, max_value=20)


class CartItemSerializer(serializers.ModelSerializer):
    name = serializers.CharField(source="menu_item.name", read_only=True)
    image = serializers.ImageField(source="menu_item.image", read_only=True)
    food_type = serializers.CharField(source="menu_item.food_type", read_only=True)
    variant = MenuVariantSerializer(read_only=True)
    addons = MenuAddonSerializer(many=True, read_only=True)
    unit_price = serializers.SerializerMethodField()
    line_total = serializers.SerializerMethodField()
    is_available = serializers.BooleanField(source="menu_item.in_stock", read_only=True)

    class Meta:
        model = CartItem
        fields = ("id", "menu_item", "name", "image", "food_type", "variant", "addons", "quantity",
                  "special_instructions", "unit_price", "line_total", "is_available")

    def get_unit_price(self, obj):
        base = obj.variant.price if obj.variant else obj.menu_item.effective_price
        return str(base + sum(a.price for a in obj.addons.all()))

    def get_line_total(self, obj):
        base = obj.variant.price if obj.variant else obj.menu_item.effective_price
        return str((base + sum(a.price for a in obj.addons.all())) * obj.quantity)


class CartSerializer(serializers.ModelSerializer):
    items = CartItemSerializer(many=True, read_only=True)
    restaurant = RestaurantListSerializer(read_only=True)
    delivery_address = AddressSerializer(read_only=True)
    item_count = serializers.IntegerField(read_only=True)
    pricing = serializers.SerializerMethodField()

    class Meta:
        model = Cart
        fields = ("id", "restaurant", "items", "item_count", "coupon_code", "delivery_address",
                  "special_instructions", "pricing", "updated_at")

    def get_pricing(self, obj):
        from .services import price_cart

        bd, _ = price_cart(obj, self.context["request"].user)
        return bd.as_dict()


class CartSettingsSerializer(serializers.Serializer):
    delivery_address_id = serializers.UUIDField(required=False, allow_null=True)
    coupon_code = serializers.CharField(required=False, allow_blank=True, max_length=30)
    special_instructions = serializers.CharField(required=False, allow_blank=True, max_length=255)
