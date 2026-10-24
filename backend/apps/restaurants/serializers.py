from decimal import Decimal

from django.contrib.gis.geos import Point
from django.utils import timezone
from rest_framework import serializers

from apps.accounts.serializers import PhoneField
from apps.core.images import compress_image

from .models import (
    Banner,
    Cuisine,
    DocumentType,
    Restaurant,
    RestaurantCategory,
    RestaurantDocument,
    RestaurantHoliday,
    RestaurantHours,
)


class CuisineSerializer(serializers.ModelSerializer):
    class Meta:
        model = Cuisine
        fields = ("id", "name", "slug", "icon")


class RestaurantCategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = RestaurantCategory
        fields = ("id", "name", "slug", "image")


class RestaurantHoursSerializer(serializers.ModelSerializer):
    class Meta:
        model = RestaurantHours
        fields = ("id", "weekday", "open_time", "close_time", "is_closed")
        read_only_fields = ("id",)


class RestaurantHolidaySerializer(serializers.ModelSerializer):
    class Meta:
        model = RestaurantHoliday
        fields = ("id", "date", "reason")
        read_only_fields = ("id",)


class RestaurantListSerializer(serializers.ModelSerializer):
    """Compact card used in lists / home sections."""

    cuisines = serializers.SlugRelatedField(slug_field="name", many=True, read_only=True)
    distance_km = serializers.SerializerMethodField()
    delivery_time_min = serializers.SerializerMethodField()
    delivery_fee = serializers.SerializerMethodField()
    is_open_now = serializers.SerializerMethodField()
    offer_text = serializers.SerializerMethodField()

    class Meta:
        model = Restaurant
        fields = ("id", "name", "slug", "logo", "cover_image", "cuisines", "rating_avg", "rating_count",
                  "cost_for_two", "is_pure_veg", "is_promoted", "is_featured", "distance_km", "delivery_time_min",
                  "delivery_fee", "min_order_amount", "is_open_now", "offer_text", "city", "address_line2")

    def get_distance_km(self, obj):
        d = getattr(obj, "distance", None)
        return round(d.km, 2) if d is not None else None

    def get_delivery_time_min(self, obj):
        d = getattr(obj, "distance", None)
        travel = (d.km / 20.0 * 60) if d is not None else 15  # 20 km/h average, traffic independent
        return int(obj.avg_preparation_minutes + travel + 5)

    def get_delivery_fee(self, obj):
        from apps.orders.pricing import delivery_fee_for_distance

        d = getattr(obj, "distance", None)
        return str(delivery_fee_for_distance(Decimal(str(d.km)) if d is not None else Decimal("2")))

    def get_is_open_now(self, obj):
        return obj.can_accept_orders_now()

    def get_offer_text(self, obj):
        offer = getattr(obj, "_best_offer", None)
        if offer is None:
            from apps.coupons.services import best_offer_text

            offer = best_offer_text(obj)
            obj._best_offer = offer
        return offer


class RestaurantDetailSerializer(RestaurantListSerializer):
    categories = RestaurantCategorySerializer(many=True, read_only=True)
    hours = RestaurantHoursSerializer(many=True, read_only=True)
    lat = serializers.FloatField(source="latitude", read_only=True)
    lng = serializers.FloatField(source="longitude", read_only=True)
    menu = serializers.SerializerMethodField()

    class Meta(RestaurantListSerializer.Meta):
        fields = RestaurantListSerializer.Meta.fields + (
            "description", "restaurant_type", "categories", "address_line1", "landmark", "lat", "lng",
            "packaging_charge", "avg_preparation_minutes", "hours", "fssai_number", "total_orders", "menu")

    def get_menu(self, obj):
        from apps.menu.serializers import MenuCategoryWithItemsSerializer

        cats = obj.menu_categories.filter(is_active=True).prefetch_related(
            "items__variant_groups__options", "items__addon_groups__options")
        return MenuCategoryWithItemsSerializer(cats, many=True, context=self.context).data


class BannerSerializer(serializers.ModelSerializer):
    class Meta:
        model = Banner
        fields = ("id", "title", "subtitle", "image", "restaurant", "coupon_code", "deep_link")


# ---------------------------------------------------------------- partner side

class PartnerRestaurantSerializer(serializers.ModelSerializer):
    latitude = serializers.FloatField(write_only=True, required=False)
    longitude = serializers.FloatField(write_only=True, required=False)
    lat = serializers.FloatField(source="latitude", read_only=True)
    lng = serializers.FloatField(source="longitude", read_only=True)
    phone = PhoneField()
    cuisine_ids = serializers.PrimaryKeyRelatedField(queryset=Cuisine.objects.filter(is_active=True), many=True,
                                                     source="cuisines", required=False)
    category_ids = serializers.PrimaryKeyRelatedField(queryset=RestaurantCategory.objects.filter(is_active=True),
                                                      many=True, source="categories", required=False)
    cuisines = CuisineSerializer(many=True, read_only=True)
    categories = RestaurantCategorySerializer(many=True, read_only=True)
    hours = RestaurantHoursSerializer(many=True, read_only=True)
    effective_commission_percent = serializers.DecimalField(max_digits=5, decimal_places=2, read_only=True)
    is_open_now = serializers.SerializerMethodField()

    class Meta:
        model = Restaurant
        fields = ("id", "name", "slug", "restaurant_type", "description", "owner_name", "phone", "email", "logo",
                  "cover_image", "address_line1", "address_line2", "landmark", "city", "state", "postal_code",
                  "latitude", "longitude", "lat", "lng", "delivery_radius_km", "fssai_number", "fssai_expiry",
                  "gst_number", "pan_number", "bank_account_name", "bank_account_number", "bank_ifsc", "upi_id",
                  "cuisine_ids", "category_ids", "cuisines", "categories", "is_pure_veg", "min_order_amount",
                  "packaging_charge", "avg_preparation_minutes", "cost_for_two", "status", "rejection_reason",
                  "is_open", "is_accepting_orders", "temporarily_closed_until", "rating_avg", "rating_count",
                  "total_orders", "effective_commission_percent", "hours", "is_open_now", "created_at")
        read_only_fields = ("id", "slug", "status", "rejection_reason", "rating_avg", "rating_count", "total_orders",
                            "created_at")

    def get_is_open_now(self, obj):
        return obj.can_accept_orders_now()

    def validate(self, attrs):
        lat, lng = attrs.pop("latitude", None), attrs.pop("longitude", None)
        if lat is not None and lng is not None:
            attrs["location"] = Point(lng, lat, srid=4326)
        elif self.instance is None:
            raise serializers.ValidationError({"latitude": "GPS location is required."})
        for f in ("logo", "cover_image"):
            if attrs.get(f):
                attrs[f] = compress_image(attrs[f])
        return attrs

    def create(self, validated):
        validated["owner"] = self.context["request"].user
        cuisines = validated.pop("cuisines", [])
        categories = validated.pop("categories", [])
        r = Restaurant.objects.create(**validated)
        r.cuisines.set(cuisines)
        r.categories.set(categories)
        # default hours 9:00 - 23:00 every day; owner can edit
        from datetime import time

        RestaurantHours.objects.bulk_create([
            RestaurantHours(restaurant=r, weekday=d, open_time=time(9, 0), close_time=time(23, 0)) for d in range(7)
        ])
        return r

    def update(self, instance, validated):
        if "temporarily_closed_until" in validated and validated["temporarily_closed_until"]:
            if validated["temporarily_closed_until"] < timezone.now():
                validated["temporarily_closed_until"] = None
        return super().update(instance, validated)


class RestaurantDocumentSerializer(serializers.ModelSerializer):
    doc_type = serializers.ChoiceField(choices=DocumentType.choices)

    class Meta:
        model = RestaurantDocument
        fields = ("id", "doc_type", "file", "number", "status", "rejection_reason", "verified_at", "created_at")
        read_only_fields = ("id", "status", "rejection_reason", "verified_at", "created_at")


class HoursBulkSerializer(serializers.Serializer):
    hours = RestaurantHoursSerializer(many=True)
