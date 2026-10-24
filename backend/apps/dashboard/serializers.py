from rest_framework import serializers

from apps.accounts.models import User
from apps.core.models import PlatformSettings
from apps.coupons.models import Coupon
from apps.orders.models import Order
from apps.restaurants.models import Banner, Restaurant, RestaurantDocument
from apps.riders.models import RiderDocument, RiderProfile


class AdminUserSerializer(serializers.ModelSerializer):
    total_orders = serializers.IntegerField(source="customer_profile.total_orders", read_only=True, default=0)

    class Meta:
        model = User
        fields = ("id", "phone", "email", "full_name", "role", "is_active", "is_suspended", "suspension_reason",
                  "is_phone_verified", "total_orders", "last_login", "created_at")
        read_only_fields = ("id", "phone", "role", "is_active", "is_suspended", "suspension_reason", "last_login",
                            "created_at")


class AdminRestaurantDocSerializer(serializers.ModelSerializer):
    class Meta:
        model = RestaurantDocument
        fields = ("id", "doc_type", "file", "number", "status", "rejection_reason", "verified_at")


class AdminRestaurantSerializer(serializers.ModelSerializer):
    owner_phone = serializers.CharField(source="owner.phone", read_only=True)
    documents = AdminRestaurantDocSerializer(many=True, read_only=True)
    lat = serializers.FloatField(source="latitude", read_only=True)
    lng = serializers.FloatField(source="longitude", read_only=True)
    effective_commission_percent = serializers.DecimalField(max_digits=5, decimal_places=2, read_only=True)

    class Meta:
        model = Restaurant
        fields = ("id", "name", "slug", "restaurant_type", "owner", "owner_phone", "owner_name", "phone", "email",
                  "logo", "cover_image", "address_line1", "address_line2", "city", "state", "postal_code", "lat", "lng",
                  "fssai_number", "gst_number", "pan_number", "bank_account_name", "bank_account_number", "bank_ifsc",
                  "upi_id", "is_pure_veg", "min_order_amount", "packaging_charge", "avg_preparation_minutes",
                  "commission_percent", "effective_commission_percent", "status", "rejection_reason", "approved_at",
                  "is_open", "is_accepting_orders", "is_featured", "is_promoted", "rating_avg", "rating_count",
                  "total_orders", "documents", "created_at")
        read_only_fields = ("id", "slug", "owner", "rating_avg", "rating_count", "total_orders", "created_at")


class AdminRiderDocSerializer(serializers.ModelSerializer):
    class Meta:
        model = RiderDocument
        fields = ("id", "doc_type", "file", "number", "status", "rejection_reason", "verified_at")


class AdminRiderSerializer(serializers.ModelSerializer):
    phone = serializers.CharField(source="user.phone", read_only=True)
    is_suspended = serializers.BooleanField(source="user.is_suspended", read_only=True)
    documents = AdminRiderDocSerializer(many=True, read_only=True)
    lat = serializers.SerializerMethodField()
    lng = serializers.SerializerMethodField()

    class Meta:
        model = RiderProfile
        fields = ("id", "user", "phone", "full_name", "photo", "email", "city", "address", "vehicle_type",
                  "vehicle_number", "driving_licence_number", "driving_licence_expiry", "aadhaar_last4", "pan_number",
                  "bank_account_name", "bank_account_number", "bank_ifsc", "upi_id", "emergency_contact_name",
                  "emergency_contact_phone", "status", "rejection_reason", "approved_at", "is_online",
                  "is_on_delivery", "is_suspended", "rating_avg", "rating_count", "total_deliveries",
                  "total_rejections", "wallet_balance", "cod_cash_in_hand", "lat", "lng", "location_updated_at",
                  "documents", "created_at")
        read_only_fields = fields

    def get_lat(self, o):
        return o.current_location.y if o.current_location else None

    def get_lng(self, o):
        return o.current_location.x if o.current_location else None


class AdminOrderSerializer(serializers.ModelSerializer):
    customer_phone = serializers.CharField(source="customer.phone", read_only=True)
    customer_name = serializers.CharField(source="customer.full_name", read_only=True)
    rider_name = serializers.CharField(source="rider.full_name", read_only=True, default=None)
    status_display = serializers.CharField(source="get_status_display", read_only=True)
    items = serializers.SerializerMethodField()
    rider_location = serializers.SerializerMethodField()

    class Meta:
        model = Order
        fields = ("id", "order_number", "status", "status_display", "customer", "customer_phone", "customer_name",
                  "restaurant", "restaurant_name", "rider", "rider_name", "items", "item_total", "delivery_fee",
                  "coupon_code", "coupon_discount", "total_amount", "commission_amount", "restaurant_payout",
                  "rider_payout", "payment_method", "is_paid", "delivery_address_text", "distance_km",
                  "placed_at", "accepted_at", "ready_at", "picked_up_at", "delivered_at", "estimated_delivery_at",
                  "cancelled_by", "cancellation_reason", "rejection_reason", "rider_location", "created_at")

    def get_items(self, o):
        return [{"name": i.name, "qty": i.quantity, "variant": i.variant_name, "total": str(i.line_total)}
                for i in o.items.all()]

    def get_rider_location(self, o):
        if not o.rider_id:
            return None
        from apps.tracking.store import get_rider_location

        return get_rider_location(o.rider_id)


class AdminCouponSerializer(serializers.ModelSerializer):
    class Meta:
        model = Coupon
        fields = "__all__"
        read_only_fields = ("id", "used_count", "created_at", "updated_at")


class PlatformSettingsSerializer(serializers.ModelSerializer):
    class Meta:
        model = PlatformSettings
        exclude = ("id",)


class DecisionSerializer(serializers.Serializer):
    approve = serializers.BooleanField()
    reason = serializers.CharField(required=False, allow_blank=True, max_length=500, default="")


class SuspendSerializer(serializers.Serializer):
    suspend = serializers.BooleanField()
    reason = serializers.CharField(required=False, allow_blank=True, max_length=255, default="")


class AdminBannerSerializer(serializers.ModelSerializer):
    class Meta:
        model = Banner
        fields = ("id", "title", "subtitle", "image", "restaurant", "coupon_code", "deep_link", "city", "sort_order",
                  "is_active", "starts_at", "ends_at", "created_at")
        read_only_fields = ("id", "created_at")
