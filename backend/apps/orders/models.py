import secrets
import uuid
from decimal import Decimal

from django.conf import settings
from django.contrib.gis.db import models as gis_models
from django.db import models
from django.utils import timezone

from apps.core.models import MoneyField, TimeStampedModel
from apps.menu.models import FoodType, MenuItem
from apps.restaurants.models import Restaurant


class OrderStatus(models.TextChoices):
    PENDING_PAYMENT = "pending_payment", "Awaiting payment"
    PLACED = "placed", "Order placed"
    RESTAURANT_ACCEPTED = "restaurant_accepted", "Restaurant accepted"
    PREPARING = "preparing", "Preparing"
    READY_FOR_PICKUP = "ready_for_pickup", "Food ready"
    RIDER_ASSIGNED = "rider_assigned", "Rider assigned"
    PICKED_UP = "picked_up", "Picked up"
    OUT_FOR_DELIVERY = "out_for_delivery", "On the way"
    NEAR_DESTINATION = "near_destination", "Near destination"
    DELIVERED = "delivered", "Delivered"
    REJECTED = "rejected", "Rejected by restaurant"
    CANCELLED = "cancelled", "Cancelled"
    PAYMENT_FAILED = "payment_failed", "Payment failed"
    DELIVERY_FAILED = "delivery_failed", "Delivery failed"


ACTIVE_STATUSES = (
    OrderStatus.PLACED, OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
    OrderStatus.RIDER_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION,
)
TERMINAL_STATUSES = (OrderStatus.DELIVERED, OrderStatus.REJECTED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED,
                     OrderStatus.DELIVERY_FAILED)
RIDER_ACTIVE_STATUSES = (OrderStatus.RIDER_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY,
                         OrderStatus.NEAR_DESTINATION)

# Centralised state machine. Keys: current status; values: allowed next statuses.
TRANSITIONS: dict[str, set[str]] = {
    OrderStatus.PENDING_PAYMENT: {OrderStatus.PLACED, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED},
    OrderStatus.PLACED: {OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED},
    OrderStatus.RESTAURANT_ACCEPTED: {OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED,
                                      OrderStatus.RIDER_ASSIGNED},
    OrderStatus.PREPARING: {OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED, OrderStatus.RIDER_ASSIGNED},
    OrderStatus.READY_FOR_PICKUP: {OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED},
    OrderStatus.RIDER_ASSIGNED: {OrderStatus.PICKED_UP, OrderStatus.READY_FOR_PICKUP, OrderStatus.PREPARING,
                                 OrderStatus.CANCELLED},
    OrderStatus.PICKED_UP: {OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION, OrderStatus.DELIVERED,
                            OrderStatus.DELIVERY_FAILED, OrderStatus.CANCELLED},
    OrderStatus.OUT_FOR_DELIVERY: {OrderStatus.NEAR_DESTINATION, OrderStatus.DELIVERED, OrderStatus.DELIVERY_FAILED,
                                   OrderStatus.CANCELLED},
    OrderStatus.NEAR_DESTINATION: {OrderStatus.DELIVERED, OrderStatus.DELIVERY_FAILED, OrderStatus.CANCELLED},
    OrderStatus.DELIVERED: set(),
    OrderStatus.REJECTED: set(),
    OrderStatus.CANCELLED: set(),
    OrderStatus.PAYMENT_FAILED: {OrderStatus.PLACED},  # retry payment on same order
    OrderStatus.DELIVERY_FAILED: set(),
}


class PaymentMethod(models.TextChoices):
    COD = "cod", "Cash on delivery"
    UPI = "upi", "UPI"
    CARD = "card", "Credit / debit card"
    NETBANKING = "netbanking", "Net banking"
    WALLET = "wallet", "Wallet"


ONLINE_METHODS = (PaymentMethod.UPI, PaymentMethod.CARD, PaymentMethod.NETBANKING, PaymentMethod.WALLET)


class CancelledBy(models.TextChoices):
    CUSTOMER = "customer", "Customer"
    RESTAURANT = "restaurant", "Restaurant"
    RIDER = "rider", "Rider"
    ADMIN = "admin", "Admin"
    SYSTEM = "system", "System"


def generate_order_number() -> str:
    # LR + 8 digits; collision checked by unique constraint + retry in service
    return "LR" + str(secrets.randbelow(10**8)).zfill(8)


class Order(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    order_number = models.CharField(max_length=12, unique=True, default=generate_order_number, db_index=True)
    customer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="orders")
    restaurant = models.ForeignKey(Restaurant, on_delete=models.PROTECT, related_name="orders")
    rider = models.ForeignKey("riders.RiderProfile", null=True, blank=True, on_delete=models.SET_NULL,
                              related_name="orders")
    status = models.CharField(max_length=24, choices=OrderStatus.choices, default=OrderStatus.PENDING_PAYMENT,
                              db_index=True)
    idempotency_key = models.CharField(max_length=64, null=True, blank=True)

    # delivery address snapshot (addresses can be edited later; the order keeps what it was delivered to)
    delivery_address = models.ForeignKey("accounts.Address", null=True, blank=True, on_delete=models.SET_NULL)
    delivery_name = models.CharField(max_length=120, blank=True, default="")
    delivery_phone = models.CharField(max_length=16, blank=True, default="")
    delivery_address_text = models.CharField(max_length=512)
    delivery_landmark = models.CharField(max_length=255, blank=True, default="")
    delivery_location = gis_models.PointField(geography=True, srid=4326)
    delivery_instructions = models.CharField(max_length=255, blank=True, default="")
    is_contactless = models.BooleanField(default=False)

    # restaurant snapshot
    restaurant_name = models.CharField(max_length=120)
    restaurant_address_text = models.CharField(max_length=512, blank=True, default="")
    restaurant_location = gis_models.PointField(geography=True, srid=4326)

    # money (all Decimal)
    item_total = MoneyField()
    packaging_charge = MoneyField()
    delivery_fee = MoneyField()
    delivery_discount = MoneyField()
    platform_fee = MoneyField()
    tax_percent = models.DecimalField(max_digits=5, decimal_places=2, default=Decimal("0.00"))
    tax_amount = MoneyField()
    coupon = models.ForeignKey("coupons.Coupon", null=True, blank=True, on_delete=models.SET_NULL)
    coupon_code = models.CharField(max_length=30, blank=True, default="")
    coupon_discount = MoneyField()
    total_amount = MoneyField()
    distance_km = models.DecimalField(max_digits=6, decimal_places=2, default=Decimal("0.00"))

    # financial split (computed at placement, finalised at delivery)
    commission_percent = models.DecimalField(max_digits=5, decimal_places=2, default=Decimal("0.00"))
    commission_amount = MoneyField()
    restaurant_payout = MoneyField(help_text="Amount owed to the restaurant for this order")
    rider_payout = MoneyField(help_text="Rider earning for this delivery")

    payment_method = models.CharField(max_length=12, choices=PaymentMethod.choices, default=PaymentMethod.COD)
    is_paid = models.BooleanField(default=False)
    paid_at = models.DateTimeField(null=True, blank=True)

    # verification codes (shown to customer / restaurant, verified server-side)
    pickup_otp = models.CharField(max_length=6, blank=True, default="")
    delivery_otp = models.CharField(max_length=6, blank=True, default="")
    otp_attempts = models.PositiveSmallIntegerField(default=0, help_text="Wrong pickup/delivery OTP entries")
    otp_locked_at = models.DateTimeField(null=True, blank=True)

    # timing
    placed_at = models.DateTimeField(null=True, blank=True, db_index=True)
    accepted_at = models.DateTimeField(null=True, blank=True)
    preparation_minutes = models.PositiveSmallIntegerField(default=20)
    ready_at = models.DateTimeField(null=True, blank=True)
    picked_up_at = models.DateTimeField(null=True, blank=True)
    delivered_at = models.DateTimeField(null=True, blank=True)
    estimated_delivery_at = models.DateTimeField(null=True, blank=True)

    # cancellation / rejection
    cancelled_by = models.CharField(max_length=12, choices=CancelledBy.choices, blank=True, default="")
    cancellation_reason = models.CharField(max_length=255, blank=True, default="")
    cancellation_fee = MoneyField()
    rejection_reason = models.CharField(max_length=255, blank=True, default="")

    customer_note = models.CharField(max_length=255, blank=True, default="")
    delivery_proof = models.ImageField(upload_to="orders/proof/%Y/%m/", blank=True, null=True)
    is_reviewed = models.BooleanField(default=False)
    rider_assignment_attempts = models.PositiveSmallIntegerField(default=0)
    last_assignment_attempt_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["customer", "-created_at"]),
            models.Index(fields=["restaurant", "status", "-created_at"]),
            models.Index(fields=["rider", "status"]),
            models.Index(fields=["status", "-placed_at"]),
        ]
        constraints = [
            models.UniqueConstraint(fields=["customer", "idempotency_key"], condition=models.Q(idempotency_key__isnull=False),
                                    name="uniq_order_idempotency_per_customer"),
        ]

    def __str__(self):
        return self.order_number

    @property
    def is_active(self):
        return self.status in ACTIVE_STATUSES

    @property
    def is_terminal(self):
        return self.status in TERMINAL_STATUSES

    @property
    def is_online_payment(self):
        return self.payment_method in ONLINE_METHODS

    def can_transition(self, new_status: str) -> bool:
        return new_status in TRANSITIONS.get(self.status, set())

    @property
    def customer_cancellable(self) -> bool:
        from apps.core.models import PlatformSettings

        if self.status not in (OrderStatus.PLACED, OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PENDING_PAYMENT):
            return False
        if self.status == OrderStatus.PLACED or self.status == OrderStatus.PENDING_PAYMENT:
            return True
        window = PlatformSettings.load().customer_cancel_window_seconds
        ref = self.placed_at or self.created_at
        return (timezone.now() - ref).total_seconds() <= window


class OrderItem(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    order = models.ForeignKey(Order, on_delete=models.CASCADE, related_name="items")
    menu_item = models.ForeignKey(MenuItem, null=True, on_delete=models.SET_NULL, related_name="order_items")
    name = models.CharField(max_length=120)
    image = models.CharField(max_length=255, blank=True, default="")
    food_type = models.CharField(max_length=10, choices=FoodType.choices, default=FoodType.VEG)
    variant_name = models.CharField(max_length=60, blank=True, default="")
    addon_names = models.JSONField(default=list, blank=True)
    quantity = models.PositiveSmallIntegerField()
    unit_price = MoneyField()
    addons_price = MoneyField()
    packaging_charge = MoneyField()
    line_total = MoneyField()
    special_instructions = models.CharField(max_length=255, blank=True, default="")

    def __str__(self):
        return f"{self.quantity} x {self.name}"


class OrderStatusHistory(models.Model):
    order = models.ForeignKey(Order, on_delete=models.CASCADE, related_name="status_history")
    from_status = models.CharField(max_length=24, blank=True, default="")
    to_status = models.CharField(max_length=24, choices=OrderStatus.choices)
    changed_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
                                   related_name="+")
    actor_role = models.CharField(max_length=16, blank=True, default="")
    note = models.CharField(max_length=255, blank=True, default="")
    metadata = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["created_at"]
        verbose_name_plural = "Order status histories"


class AssignmentStatus(models.TextChoices):
    OFFERED = "offered", "Offered"
    ACCEPTED = "accepted", "Accepted"
    REJECTED = "rejected", "Rejected"
    EXPIRED = "expired", "Expired"
    CANCELLED = "cancelled", "Cancelled"
    REASSIGNED = "reassigned", "Reassigned"


class RiderAssignment(TimeStampedModel):
    """An offer made to a rider for a specific order. One accepted assignment per order."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    order = models.ForeignKey(Order, on_delete=models.CASCADE, related_name="assignments")
    rider = models.ForeignKey("riders.RiderProfile", on_delete=models.CASCADE, related_name="assignments")
    status = models.CharField(max_length=12, choices=AssignmentStatus.choices, default=AssignmentStatus.OFFERED)
    distance_to_restaurant_km = models.DecimalField(max_digits=6, decimal_places=2, default=Decimal("0.00"))
    offered_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField()
    responded_at = models.DateTimeField(null=True, blank=True)
    rejection_reason = models.CharField(max_length=120, blank=True, default="")
    is_manual = models.BooleanField(default=False, help_text="Assigned by admin")

    class Meta:
        ordering = ["-offered_at"]
        indexes = [models.Index(fields=["rider", "status"]), models.Index(fields=["order", "status"])]
        constraints = [
            models.UniqueConstraint(fields=["order"], condition=models.Q(status="accepted"),
                                    name="uniq_accepted_assignment_per_order"),
        ]
