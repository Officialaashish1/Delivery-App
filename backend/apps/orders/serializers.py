from rest_framework import serializers

from apps.core.geo import point_to_latlng

from .models import Order, OrderItem, OrderStatus, OrderStatusHistory, PaymentMethod, RiderAssignment

# The customer-facing timeline (maps internal statuses to the 9 visible stages)
TIMELINE_STAGES = [
    ("placed", "Order placed", [OrderStatus.PLACED]),
    ("accepted", "Restaurant accepted", [OrderStatus.RESTAURANT_ACCEPTED]),
    ("preparing", "Preparing your food", [OrderStatus.PREPARING]),
    ("ready", "Food ready", [OrderStatus.READY_FOR_PICKUP]),
    ("rider_assigned", "Rider assigned", [OrderStatus.RIDER_ASSIGNED]),
    ("picked_up", "Picked up", [OrderStatus.PICKED_UP]),
    ("on_the_way", "On the way", [OrderStatus.OUT_FOR_DELIVERY]),
    ("near", "Near your location", [OrderStatus.NEAR_DESTINATION]),
    ("delivered", "Delivered", [OrderStatus.DELIVERED]),
]
STATUS_RANK = {st: i for i, (_, _, sts) in enumerate(TIMELINE_STAGES) for st in sts}


def build_timeline(order: Order):
    history = {h.to_status: h.created_at for h in order.status_history.all()}
    current_rank = STATUS_RANK.get(order.status, -1)
    out = []
    for i, (key, label, statuses) in enumerate(TIMELINE_STAGES):
        reached_at = next((history[s] for s in statuses if s in history), None)
        if key == "rider_assigned" and order.rider_id and reached_at is None and current_rank >= 1:
            reached_at = next((h.created_at for h in order.status_history.all() if h.note == "Rider assigned"), None)
        done = i <= current_rank or reached_at is not None
        if order.is_terminal and order.status != OrderStatus.DELIVERED:
            done = reached_at is not None
        out.append({"key": key, "label": label, "done": done, "current": i == current_rank,
                    "at": reached_at})
    return out


class OrderItemSerializer(serializers.ModelSerializer):
    class Meta:
        model = OrderItem
        fields = ("id", "menu_item", "name", "image", "food_type", "variant_name", "addon_names", "quantity",
                  "unit_price", "addons_price", "packaging_charge", "line_total", "special_instructions")


class OrderStatusHistorySerializer(serializers.ModelSerializer):
    class Meta:
        model = OrderStatusHistory
        fields = ("from_status", "to_status", "actor_role", "note", "created_at")


class RiderPublicSerializer(serializers.Serializer):
    """What a customer may see about their rider. No phone number: calls go through the masked endpoint."""

    id = serializers.UUIDField()
    full_name = serializers.CharField()
    photo = serializers.ImageField(allow_null=True)
    rating_avg = serializers.DecimalField(max_digits=3, decimal_places=2)
    total_deliveries = serializers.IntegerField()
    vehicle_type = serializers.CharField()
    vehicle_number = serializers.CharField()


class OrderListSerializer(serializers.ModelSerializer):
    status_display = serializers.CharField(source="get_status_display", read_only=True)
    item_count = serializers.SerializerMethodField()
    items_summary = serializers.SerializerMethodField()
    restaurant_logo = serializers.ImageField(source="restaurant.logo", read_only=True)
    restaurant_slug = serializers.CharField(source="restaurant.slug", read_only=True)
    can_cancel = serializers.BooleanField(source="customer_cancellable", read_only=True)

    class Meta:
        model = Order
        fields = ("id", "order_number", "status", "status_display", "restaurant", "restaurant_name", "restaurant_logo",
                  "restaurant_slug", "total_amount", "payment_method", "is_paid", "item_count", "items_summary",
                  "placed_at", "created_at", "delivered_at", "estimated_delivery_at", "is_reviewed", "can_cancel",
                  "is_active")

    def get_item_count(self, obj):
        return sum(i.quantity for i in obj.items.all())

    def get_items_summary(self, obj):
        return ", ".join(f"{i.quantity} x {i.name}" for i in obj.items.all())[:140]


class OrderDetailSerializer(OrderListSerializer):
    items = OrderItemSerializer(many=True, read_only=True)
    timeline = serializers.SerializerMethodField()
    status_history = OrderStatusHistorySerializer(many=True, read_only=True)
    rider = serializers.SerializerMethodField()
    delivery_location = serializers.SerializerMethodField()
    restaurant_location = serializers.SerializerMethodField()
    delivery_otp = serializers.SerializerMethodField()
    pricing = serializers.SerializerMethodField()
    invoice_url = serializers.SerializerMethodField()
    refund_status = serializers.SerializerMethodField()

    class Meta(OrderListSerializer.Meta):
        fields = OrderListSerializer.Meta.fields + (
            "items", "timeline", "status_history", "rider", "delivery_name", "delivery_phone", "delivery_address_text",
            "delivery_landmark", "delivery_location", "delivery_instructions", "is_contactless",
            "restaurant_address_text", "restaurant_location", "delivery_otp", "pricing", "distance_km",
            "preparation_minutes", "accepted_at", "ready_at", "picked_up_at", "cancelled_by", "cancellation_reason",
            "cancellation_fee", "rejection_reason", "customer_note", "invoice_url", "refund_status")

    def get_timeline(self, obj):
        return build_timeline(obj)

    def get_rider(self, obj):
        if obj.rider is None or obj.status not in (
                OrderStatus.RIDER_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.NEAR_DESTINATION, OrderStatus.DELIVERED, OrderStatus.PREPARING,
                OrderStatus.READY_FOR_PICKUP, OrderStatus.RESTAURANT_ACCEPTED):
            return None
        return RiderPublicSerializer(obj.rider, context=self.context).data

    def get_delivery_location(self, obj):
        return point_to_latlng(obj.delivery_location)

    def get_restaurant_location(self, obj):
        return point_to_latlng(obj.restaurant_location)

    def get_delivery_otp(self, obj):
        # Only the customer sees the delivery OTP, and only while a rider is on the way.
        request = self.context.get("request")
        if request and request.user.id == obj.customer_id and obj.status in (
                OrderStatus.RIDER_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.NEAR_DESTINATION):
            return obj.delivery_otp
        return None

    def get_pricing(self, obj):
        return {
            "item_total": str(obj.item_total), "packaging_charge": str(obj.packaging_charge),
            "delivery_fee": str(obj.delivery_fee), "delivery_discount": str(obj.delivery_discount),
            "platform_fee": str(obj.platform_fee), "tax_percent": str(obj.tax_percent),
            "tax_amount": str(obj.tax_amount), "coupon_code": obj.coupon_code,
            "coupon_discount": str(obj.coupon_discount), "total": str(obj.total_amount),
        }

    def get_invoice_url(self, obj):
        request = self.context.get("request")
        path = f"/api/v1/orders/{obj.id}/invoice/"
        return request.build_absolute_uri(path) if request else path

    def get_refund_status(self, obj):
        r = obj.refunds.order_by("-created_at").first()
        if r is None:
            return None
        return {"amount": str(r.amount), "status": r.status, "reason": r.reason, "processed_at": r.processed_at}


class PlaceOrderSerializer(serializers.Serializer):
    address_id = serializers.UUIDField()
    payment_method = serializers.ChoiceField(choices=PaymentMethod.choices)
    idempotency_key = serializers.CharField(max_length=64, required=False, allow_blank=True)
    is_contactless = serializers.BooleanField(default=False)
    delivery_instructions = serializers.CharField(max_length=255, required=False, allow_blank=True, default="")
    customer_note = serializers.CharField(max_length=255, required=False, allow_blank=True, default="")


class CancelOrderSerializer(serializers.Serializer):
    reason = serializers.CharField(max_length=255)


class PlaceOrderResponseSerializer(serializers.Serializer):
    order = OrderDetailSerializer()
    payment = serializers.DictField(allow_null=True)


# ---------------------------------------------------------------- partner

class PartnerOrderSerializer(serializers.ModelSerializer):
    items = OrderItemSerializer(many=True, read_only=True)
    status_display = serializers.CharField(source="get_status_display", read_only=True)
    customer_name = serializers.CharField(source="delivery_name", read_only=True)
    rider = RiderPublicSerializer(read_only=True)
    pickup_otp = serializers.SerializerMethodField()
    time_since_placed_s = serializers.SerializerMethodField()

    class Meta:
        model = Order
        fields = ("id", "order_number", "status", "status_display", "items", "customer_name", "customer_note",
                  "delivery_address_text", "is_contactless", "item_total", "packaging_charge", "coupon_discount", "total_amount", "restaurant_payout",
                  "commission_amount", "payment_method", "is_paid", "rider", "pickup_otp", "preparation_minutes",
                  "placed_at", "accepted_at", "ready_at", "picked_up_at", "delivered_at", "estimated_delivery_at",
                  "rejection_reason", "cancellation_reason", "cancelled_by", "time_since_placed_s", "created_at")

    def get_pickup_otp(self, obj):
        return obj.pickup_otp if obj.status in (OrderStatus.READY_FOR_PICKUP, OrderStatus.RIDER_ASSIGNED,
                                                OrderStatus.PREPARING, OrderStatus.RESTAURANT_ACCEPTED) else None

    def get_time_since_placed_s(self, obj):
        from django.utils import timezone

        ref = obj.placed_at or obj.created_at
        return int((timezone.now() - ref).total_seconds())


class AcceptOrderSerializer(serializers.Serializer):
    preparation_minutes = serializers.IntegerField(min_value=5, max_value=120, required=False)


class RejectOrderSerializer(serializers.Serializer):
    reason = serializers.CharField(max_length=255)


# ---------------------------------------------------------------- rider

class RiderOrderSerializer(serializers.ModelSerializer):
    items = OrderItemSerializer(many=True, read_only=True)
    status_display = serializers.CharField(source="get_status_display", read_only=True)
    restaurant_location = serializers.SerializerMethodField()
    delivery_location = serializers.SerializerMethodField()
    restaurant_phone = serializers.CharField(source="restaurant.phone", read_only=True)
    collect_amount = serializers.SerializerMethodField()

    class Meta:
        model = Order
        fields = ("id", "order_number", "status", "status_display", "items", "restaurant_name",
                  "restaurant_address_text", "restaurant_location", "restaurant_phone", "delivery_name",
                  "delivery_address_text", "delivery_landmark", "delivery_location", "delivery_instructions",
                  "is_contactless", "payment_method", "is_paid", "collect_amount", "rider_payout", "distance_km",
                  "placed_at", "accepted_at", "ready_at", "picked_up_at", "delivered_at", "estimated_delivery_at",
                  "preparation_minutes")

    def get_restaurant_location(self, obj):
        return point_to_latlng(obj.restaurant_location)

    def get_delivery_location(self, obj):
        return point_to_latlng(obj.delivery_location)

    def get_collect_amount(self, obj):
        return str(obj.total_amount) if obj.payment_method == PaymentMethod.COD and not obj.is_paid else "0.00"


class RiderOfferSerializer(serializers.ModelSerializer):
    order = RiderOrderSerializer(read_only=True)
    seconds_left = serializers.SerializerMethodField()

    class Meta:
        model = RiderAssignment
        fields = ("id", "order", "status", "distance_to_restaurant_km", "offered_at", "expires_at", "seconds_left")

    def get_seconds_left(self, obj):
        from django.utils import timezone

        return max(0, int((obj.expires_at - timezone.now()).total_seconds()))
