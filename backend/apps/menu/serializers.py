from rest_framework import serializers

from apps.core.images import compress_image

from .models import AddonGroup, MenuAddon, MenuCategory, MenuItem, MenuVariant, VariantGroup


class MenuVariantSerializer(serializers.ModelSerializer):
    class Meta:
        model = MenuVariant
        fields = ("id", "name", "price", "is_available", "is_default", "sort_order")
        read_only_fields = ("id",)


class VariantGroupSerializer(serializers.ModelSerializer):
    options = MenuVariantSerializer(many=True)

    class Meta:
        model = VariantGroup
        fields = ("id", "name", "is_required", "sort_order", "options")
        read_only_fields = ("id",)


class MenuAddonSerializer(serializers.ModelSerializer):
    class Meta:
        model = MenuAddon
        fields = ("id", "name", "price", "food_type", "is_available", "sort_order")
        read_only_fields = ("id",)


class AddonGroupSerializer(serializers.ModelSerializer):
    options = MenuAddonSerializer(many=True)

    class Meta:
        model = AddonGroup
        fields = ("id", "name", "min_select", "max_select", "sort_order", "options")
        read_only_fields = ("id",)


class MenuItemSerializer(serializers.ModelSerializer):
    """Customer-facing item with its customisation groups."""

    variant_groups = VariantGroupSerializer(many=True, read_only=True)
    addon_groups = AddonGroupSerializer(many=True, read_only=True)
    effective_price = serializers.DecimalField(max_digits=12, decimal_places=2, read_only=True)
    in_stock = serializers.BooleanField(read_only=True)
    restaurant_id = serializers.UUIDField(read_only=True)
    restaurant_name = serializers.CharField(source="restaurant.name", read_only=True)
    restaurant_slug = serializers.CharField(source="restaurant.slug", read_only=True)
    category_name = serializers.CharField(source="category.name", read_only=True)

    class Meta:
        model = MenuItem
        fields = ("id", "restaurant_id", "restaurant_name", "restaurant_slug", "category", "category_name", "name",
                  "description", "image", "food_type", "price", "discounted_price", "effective_price",
                  "packaging_charge", "preparation_minutes", "is_available", "in_stock", "is_recommended",
                  "is_bestseller", "spice_level", "calories", "tags", "rating_avg", "rating_count", "order_count",
                  "variant_groups", "addon_groups")


class MenuCategoryWithItemsSerializer(serializers.ModelSerializer):
    items = serializers.SerializerMethodField()

    class Meta:
        model = MenuCategory
        fields = ("id", "name", "description", "sort_order", "items")

    def get_items(self, obj):
        items = [i for i in obj.items.all() if i.deleted_at is None]
        return MenuItemSerializer(items, many=True, context=self.context).data


# ---------------------------------------------------------------- partner management

class PartnerMenuCategorySerializer(serializers.ModelSerializer):
    item_count = serializers.IntegerField(source="items.count", read_only=True)

    class Meta:
        model = MenuCategory
        fields = ("id", "name", "description", "sort_order", "is_active", "item_count")
        read_only_fields = ("id",)


class PartnerMenuItemSerializer(serializers.ModelSerializer):
    """Full write serializer: nested variant/addon groups are replaced wholesale on update."""

    variant_groups = VariantGroupSerializer(many=True, required=False)
    addon_groups = AddonGroupSerializer(many=True, required=False)
    effective_price = serializers.DecimalField(max_digits=12, decimal_places=2, read_only=True)

    class Meta:
        model = MenuItem
        fields = ("id", "category", "name", "description", "image", "food_type", "price", "discounted_price",
                  "effective_price", "packaging_charge", "preparation_minutes", "is_available", "stock_quantity",
                  "is_recommended", "is_bestseller", "spice_level", "calories", "tags", "sort_order", "order_count",
                  "rating_avg", "rating_count", "variant_groups", "addon_groups", "created_at")
        read_only_fields = ("id", "order_count", "rating_avg", "rating_count", "created_at")

    def validate_category(self, cat):
        if cat.restaurant_id != self.context["restaurant"].id:
            raise serializers.ValidationError("Category belongs to another restaurant.")
        return cat

    def validate(self, attrs):
        for key in ("price", "discounted_price", "packaging_charge"):
            if attrs.get(key) is not None and attrs[key] < 0:
                raise serializers.ValidationError({key: "Must not be negative."})
        for g in attrs.get("variant_groups", []):
            if any(o.get("price", 0) < 0 for o in g.get("options", [])):
                raise serializers.ValidationError({"variant_groups": "Variant prices must not be negative."})
        for g in attrs.get("addon_groups", []):
            if any(o.get("price", 0) < 0 for o in g.get("options", [])):
                raise serializers.ValidationError({"addon_groups": "Add-on prices must not be negative."})
        price = attrs.get("price", getattr(self.instance, "price", None))
        discounted = attrs.get("discounted_price", getattr(self.instance, "discounted_price", None))
        if discounted is not None and price is not None and discounted >= price:
            raise serializers.ValidationError({"discounted_price": "Must be lower than the price."})
        if attrs.get("image"):
            attrs["image"] = compress_image(attrs["image"])
        for g in attrs.get("addon_groups", []):
            if g["min_select"] > g["max_select"]:
                raise serializers.ValidationError({"addon_groups": "min_select cannot exceed max_select."})
        return attrs

    def _write_groups(self, item, variant_groups, addon_groups):
        if variant_groups is not None:
            item.variant_groups.all().delete()
            for gi, g in enumerate(variant_groups):
                opts = g.pop("options", [])
                group = VariantGroup.objects.create(item=item, sort_order=g.get("sort_order", gi),
                                                    name=g["name"], is_required=g.get("is_required", True))
                MenuVariant.objects.bulk_create([MenuVariant(group=group, **o) for o in opts])
        if addon_groups is not None:
            item.addon_groups.all().delete()
            for gi, g in enumerate(addon_groups):
                opts = g.pop("options", [])
                group = AddonGroup.objects.create(item=item, name=g["name"], min_select=g.get("min_select", 0),
                                                  max_select=g.get("max_select", 5),
                                                  sort_order=g.get("sort_order", gi))
                MenuAddon.objects.bulk_create([MenuAddon(group=group, **o) for o in opts])

    def create(self, validated):
        vg = validated.pop("variant_groups", None)
        ag = validated.pop("addon_groups", None)
        validated["restaurant"] = self.context["restaurant"]
        item = MenuItem.objects.create(**validated)
        self._write_groups(item, vg, ag)
        return item

    def update(self, instance, validated):
        vg = validated.pop("variant_groups", None)
        ag = validated.pop("addon_groups", None)
        instance = super().update(instance, validated)
        self._write_groups(instance, vg, ag)
        return instance
