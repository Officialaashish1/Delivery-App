"""
Order domain services. Every status change goes through `transition()` which enforces the
state machine, writes OrderStatusHistory, and fans out real-time + push notifications.
"""
import logging
import secrets
from datetime import timedelta
from decimal import Decimal

from django.db import IntegrityError, transaction
from django.utils import timezone

from apps.accounts.models import UserRole
from apps.core.audit import audit
from apps.core.exceptions import BusinessError, ConflictError
from apps.core.models import PlatformSettings
from apps.core.money import ZERO, percent_of, q
from apps.menu.models import MenuItem

from .models import (
    ONLINE_METHODS,
    AssignmentStatus,
    CancelledBy,
    Order,
    OrderItem,
    OrderStatus,
    OrderStatusHistory,
    PaymentMethod,
    RiderAssignment,
)
from .pricing import price_lines

log = logging.getLogger("localrush.orders")


OTP_MAX_ATTEMPTS = 5


def _otp() -> str:
    return f"{secrets.randbelow(10**4):04d}"


def _check_otp(order_id, kind: str, given: str):
    """Constant-time compare with a per-order attempt lock (H1: prevents brute-forcing 4-digit codes).
    Runs OUTSIDE the mutation transaction so a wrong attempt is persisted even though the request fails."""
    from django.db.models import F

    order = Order.objects.only("id", "order_number", "pickup_otp", "delivery_otp", "otp_attempts",
                               "otp_locked_at").get(pk=order_id)
    if order.otp_locked_at is not None:
        raise ConflictError("Too many wrong codes. This order is locked; contact support.", code="otp_locked",
                            status_code=423)
    expected = order.pickup_otp if kind == "pickup" else order.delivery_otp
    if secrets.compare_digest(expected, (given or "").strip()):
        return
    Order.objects.filter(pk=order_id).update(otp_attempts=F("otp_attempts") + 1)
    attempts = Order.objects.filter(pk=order_id).values_list("otp_attempts", flat=True).first() or 0
    if attempts >= OTP_MAX_ATTEMPTS:
        Order.objects.filter(pk=order_id, otp_locked_at__isnull=True).update(otp_locked_at=timezone.now())
        from apps.notifications.services import notify_admins

        notify_admins("OTP lockout", f"Order {order.order_number}: {OTP_MAX_ATTEMPTS} wrong {kind} codes by rider. "
                      "Verify before unlocking.", {"order_id": str(order_id)})
        audit(f"order.{kind}_otp_locked", target=order)
    raise BusinessError(f"Incorrect {kind} code.", code="otp_invalid",
                        extra={"attempts_left": max(0, OTP_MAX_ATTEMPTS - attempts)})


def _after_commit(fn, *args, **kwargs):
    transaction.on_commit(lambda: fn(*args, **kwargs))


def _fanout(order: Order, event: str, **extra):
    """Broadcast over WebSocket + schedule push notifications after the DB commit."""
    from apps.notifications.services import notify_order_event
    from apps.tracking.broadcast import broadcast_order_update

    _after_commit(broadcast_order_update, order.id, event, extra)
    _after_commit(notify_order_event, order.id, event, extra)


def transition(order: Order, new_status: str, actor=None, note: str = "", **metadata) -> Order:
    if order.status == new_status:
        return order
    if not order.can_transition(new_status):
        raise ConflictError(f"Cannot move order from '{order.get_status_display()}' to '{new_status}'.",
                            code="invalid_transition", extra={"current_status": order.status})
    old = order.status
    now = timezone.now()
    order.status = new_status
    stamps = {
        OrderStatus.PLACED: "placed_at",
        OrderStatus.RESTAURANT_ACCEPTED: "accepted_at",
        OrderStatus.READY_FOR_PICKUP: "ready_at",
        OrderStatus.PICKED_UP: "picked_up_at",
        OrderStatus.DELIVERED: "delivered_at",
    }
    update_fields = ["status", "updated_at"]
    if new_status in stamps and getattr(order, stamps[new_status]) is None:
        setattr(order, stamps[new_status], now)
        update_fields.append(stamps[new_status])
    order.save(update_fields=update_fields)
    OrderStatusHistory.objects.create(
        order=order, from_status=old, to_status=new_status, changed_by=actor,
        actor_role=getattr(actor, "role", "") if actor else "system", note=note, metadata=metadata)
    _fanout(order, f"status.{new_status}", from_status=old)
    return order


# ---------------------------------------------------------------- placement

@transaction.atomic
def place_order(user, *, address, payment_method: str, idempotency_key: str | None = None, is_contactless=False,
                delivery_instructions: str = "", customer_note: str = "") -> Order:
    from apps.cart.services import get_cart, priced_lines_for_cart

    if idempotency_key:
        existing = Order.objects.filter(idempotency_key=idempotency_key, customer=user).first()
        if existing:
            return existing
    from apps.cart.models import Cart

    Cart.objects.select_for_update().filter(user=user).first()  # serialise concurrent placements from one cart
    cart = get_cart(user)
    if cart.restaurant is None or not cart.items.exists():
        raise BusinessError("Your cart is empty.", code="cart_empty")
    restaurant = cart.restaurant
    if not restaurant.can_accept_orders_now():
        raise BusinessError(f"{restaurant.name} is not accepting orders right now.", code="restaurant_closed")
    if address.user_id != user.id:
        raise BusinessError("Invalid address.", code="validation_error")
    ps = PlatformSettings.load()
    if payment_method == PaymentMethod.COD and not ps.cod_enabled:
        raise BusinessError("Cash on delivery is currently unavailable.", code="cod_disabled")

    item_ids = list(cart.items.values_list("menu_item_id", flat=True))
    locked_items = {str(m.id): m for m in MenuItem.objects.select_for_update().filter(id__in=item_ids)}
    if cart.coupon_code:
        from apps.coupons.models import Coupon as _Coupon

        _Coupon.objects.select_for_update().filter(code=cart.coupon_code.strip().upper()).first()
    lines, problems = priced_lines_for_cart(cart)
    for ln in lines:
        mi = locked_items.get(ln.menu_item_id)
        if mi is not None and mi.stock_quantity is not None and mi.stock_quantity < ln.quantity:
            problems.append(f"Only {mi.stock_quantity} of {ln.name} left.")
    if problems:
        raise BusinessError(problems[0], code="cart_item_unavailable", extra={"problems": problems})
    bd = price_lines(lines, restaurant, address.location, cart.coupon_code, user, raise_on_coupon_error=True)
    if bd.errors:
        raise BusinessError(bd.errors[0], code="cart_invalid", extra={"errors": bd.errors})
    if payment_method == PaymentMethod.COD and bd.total > ps.cod_max_order_value:
        raise BusinessError(f"Cash on delivery is available only for orders up to ₹{ps.cod_max_order_value:.0f}.",
                            code="cod_limit")

    commission_pct = restaurant.effective_commission_percent
    restaurant_base = bd.item_total + bd.packaging_charge
    commission = percent_of(restaurant_base, commission_pct)
    coupon = None
    coupon_cost_to_restaurant = ZERO
    if bd.coupon_code:
        from apps.coupons.models import Coupon

        coupon = Coupon.objects.get(code=bd.coupon_code)
        if coupon.funded_by_restaurant:
            coupon_cost_to_restaurant = bd.coupon_discount
    restaurant_payout = q(restaurant_base - commission - coupon_cost_to_restaurant)
    rider_payout = q(ps.rider_base_pay + ps.rider_per_km_pay * max(ZERO, bd.distance_km - Decimal("1")))

    initial_status = OrderStatus.PLACED if payment_method == PaymentMethod.COD else OrderStatus.PENDING_PAYMENT
    for _ in range(5):
        try:
            with transaction.atomic():
                order = Order.objects.create(
                    customer=user, restaurant=restaurant, status=initial_status, idempotency_key=idempotency_key,
                    delivery_address=address, delivery_name=address.contact_name or user.full_name,
                    delivery_phone=address.contact_phone or user.phone, delivery_address_text=address.formatted,
                    delivery_landmark=address.landmark, delivery_location=address.location,
                    delivery_instructions=delivery_instructions or address.delivery_instructions,
                    is_contactless=is_contactless, restaurant_name=restaurant.name,
                    restaurant_address_text=f"{restaurant.address_line1}, {restaurant.city}",
                    restaurant_location=restaurant.location,
                    item_total=bd.item_total, packaging_charge=bd.packaging_charge, delivery_fee=bd.delivery_fee,
                    delivery_discount=bd.delivery_discount, platform_fee=bd.platform_fee,
                    tax_percent=bd.tax_percent, tax_amount=bd.tax_amount, coupon=coupon,
                    coupon_code=bd.coupon_code, coupon_discount=bd.coupon_discount, total_amount=bd.total,
                    distance_km=bd.distance_km, commission_percent=commission_pct, commission_amount=commission,
                    restaurant_payout=restaurant_payout, rider_payout=rider_payout,
                    payment_method=payment_method, pickup_otp=_otp(), delivery_otp=_otp(),
                    preparation_minutes=restaurant.avg_preparation_minutes, customer_note=customer_note,
                    placed_at=timezone.now() if initial_status == OrderStatus.PLACED else None,
                )
            break
        except IntegrityError as e:
            if "order_number" in str(e):
                continue
            raise
    else:
        raise ConflictError("Could not allocate an order number. Please retry.")

    OrderItem.objects.bulk_create([
        OrderItem(order=order, menu_item_id=ln.menu_item_id, name=ln.name, image=ln.image, food_type=ln.food_type,
                  variant_name=ln.variant_name, addon_names=ln.addon_names, quantity=ln.quantity,
                  unit_price=ln.unit_price, addons_price=ln.addons_price, packaging_charge=ln.packaging_charge,
                  line_total=ln.line_total, special_instructions=ln.special_instructions)
        for ln in lines
    ])
    # stock decrement (only for tracked stock; rows are locked above so this cannot oversell)
    for ln in lines:
        MenuItem.objects.filter(id=ln.menu_item_id, stock_quantity__isnull=False).update(
            stock_quantity=models_f_max(ln.quantity))
    order.estimated_delivery_at = timezone.now() + timedelta(minutes=order.preparation_minutes + _travel_minutes(order))
    order.save(update_fields=["estimated_delivery_at"])
    OrderStatusHistory.objects.create(order=order, from_status="", to_status=initial_status, changed_by=user,
                                      actor_role=UserRole.CUSTOMER)
    from apps.payments.services import create_payment_for_order

    create_payment_for_order(order)
    cart.clear()
    if initial_status == OrderStatus.PLACED:
        on_order_placed(order)
    audit("order.placed", target=order, actor=user, total=str(order.total_amount), method=payment_method)
    return order


def models_f_max(qty):
    from django.db.models import F, Func, Value

    return Func(F("stock_quantity") - Value(qty), Value(0), function="GREATEST")


def _travel_minutes(order: Order) -> int:
    return int(float(order.distance_km) / 20.0 * 60) + 5


def on_order_placed(order: Order, fanout=True):
    """Called once an order is confirmed (COD immediately, online after payment capture)."""
    if order.coupon_id:
        from apps.coupons.models import Coupon, CouponUsage

        CouponUsage.objects.get_or_create(order=order, defaults={
            "coupon": order.coupon, "user": order.customer, "discount_amount": order.coupon_discount})
        Coupon.objects.filter(pk=order.coupon_id).update(used_count=_f_plus("used_count", 1))
    if fanout:
        _fanout(order, "status.placed")


def _f_plus(field, n):
    from django.db.models import F

    return F(field) + n


class PaymentNotApplicable(ConflictError):
    """Raised when a capture arrives for an order that is already paid or no longer awaiting payment (H2)."""

    default_code = "payment_not_applicable"


@transaction.atomic
def confirm_online_payment(order_id, actor=None) -> Order:
    order = Order.objects.select_for_update(of=("self",)).get(pk=order_id)
    if order.status not in (OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED) or order.is_paid:
        raise PaymentNotApplicable("Order is not awaiting payment.")
    order.is_paid = True
    order.paid_at = timezone.now()
    order.save(update_fields=["is_paid", "paid_at"])
    transition(order, OrderStatus.PLACED, actor=actor, note="Payment captured")
    on_order_placed(order, fanout=False)
    return order


@transaction.atomic
def fail_online_payment(order_id, reason="") -> Order:
    order = Order.objects.select_for_update().get(pk=order_id)
    if order.status == OrderStatus.PENDING_PAYMENT:
        transition(order, OrderStatus.PAYMENT_FAILED, note=reason[:255])
    return order


# ---------------------------------------------------------------- restaurant actions

def _lock(order_id) -> Order:
    return Order.objects.select_for_update(of=("self",)).select_related("restaurant", "rider", "customer").get(pk=order_id)


@transaction.atomic
def restaurant_accept(order_id, actor, preparation_minutes: int | None = None) -> Order:
    order = _lock(order_id)
    _assert_restaurant_owner(order, actor)
    if preparation_minutes:
        order.preparation_minutes = max(5, min(120, int(preparation_minutes)))
        order.estimated_delivery_at = timezone.now() + timedelta(minutes=order.preparation_minutes
                                                                 + _travel_minutes(order))
        order.save(update_fields=["preparation_minutes", "estimated_delivery_at"])
    transition(order, OrderStatus.RESTAURANT_ACCEPTED, actor=actor)
    from .tasks import dispatch_order

    _after_commit(dispatch_order.delay, str(order.id))
    return order


@transaction.atomic
def restaurant_reject(order_id, actor, reason: str) -> Order:
    order = _lock(order_id)
    _assert_restaurant_owner(order, actor)
    order.rejection_reason = reason[:255]
    order.cancelled_by = CancelledBy.RESTAURANT
    order.save(update_fields=["rejection_reason", "cancelled_by"])
    transition(order, OrderStatus.REJECTED, actor=actor, note=reason)
    _release_rider(order)
    _restock(order)
    _refund_if_paid(order, "Order rejected by restaurant", actor)
    return order


@transaction.atomic
def restaurant_preparing(order_id, actor) -> Order:
    order = _lock(order_id)
    _assert_restaurant_owner(order, actor)
    return transition(order, OrderStatus.PREPARING, actor=actor)


@transaction.atomic
def restaurant_ready(order_id, actor) -> Order:
    order = _lock(order_id)
    _assert_restaurant_owner(order, actor)
    if order.status == OrderStatus.RESTAURANT_ACCEPTED:
        transition(order, OrderStatus.PREPARING, actor=actor)
    transition(order, OrderStatus.READY_FOR_PICKUP, actor=actor)
    if order.rider_id and order.assignments.filter(status=AssignmentStatus.ACCEPTED).exists():
        transition(order, OrderStatus.RIDER_ASSIGNED, actor=None, note="Rider already assigned")
    else:
        from .tasks import dispatch_order

        _after_commit(dispatch_order.delay, str(order.id))
    return order


def _assert_restaurant_owner(order: Order, actor):
    if actor.role == UserRole.ADMIN or actor.is_staff:
        return
    if order.restaurant.owner_id != actor.id:
        raise BusinessError("Not your order.", code="permission_denied", status_code=403)


# ---------------------------------------------------------------- rider dispatch

def find_candidate_riders(order: Order, exclude_ids=(), limit=5):
    from django.contrib.gis.db.models.functions import Distance
    from django.contrib.gis.measure import D

    from apps.riders.models import RiderProfile, RiderStatus

    ps = PlatformSettings.load()
    fresh = timezone.now() - timedelta(minutes=3)
    qs = RiderProfile.objects.filter(
        status=RiderStatus.APPROVED, is_online=True, user__is_suspended=False, user__is_active=True,
        current_location__isnull=False, location_updated_at__gte=fresh,
        current_location__dwithin=(order.restaurant_location, D(km=float(ps.rider_search_radius_km))),
    ).exclude(id__in=exclude_ids)
    if ps.rider_max_concurrent_orders <= 1:
        qs = qs.filter(is_on_delivery=False)
    else:
        from django.db.models import Count, Q

        qs = qs.annotate(active=Count("orders", filter=Q(orders__status__in=(
            OrderStatus.RIDER_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.NEAR_DESTINATION)))).filter(active__lt=ps.rider_max_concurrent_orders)
    return qs.annotate(distance=Distance("current_location", order.restaurant_location)).order_by("distance")[:limit]


@transaction.atomic
def offer_order_to_next_rider(order_id) -> RiderAssignment | None:
    order = _lock(order_id)
    if order.status not in (OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP):
        return None
    if order.rider_id and order.assignments.filter(status=AssignmentStatus.ACCEPTED).exists():
        return None
    if order.assignments.filter(status=AssignmentStatus.OFFERED, expires_at__gt=timezone.now()).exists():
        return None  # an offer is still pending
    tried = set(order.assignments.values_list("rider_id", flat=True))
    candidates = list(find_candidate_riders(order, exclude_ids=tried, limit=1))
    order.rider_assignment_attempts += 1
    order.last_assignment_attempt_at = timezone.now()
    order.save(update_fields=["rider_assignment_attempts", "last_assignment_attempt_at"])
    if not candidates:
        log.info("No riders available for %s (attempt %s)", order.order_number, order.rider_assignment_attempts)
        if order.rider_assignment_attempts in (3, 8, 15):
            from apps.notifications.services import notify_admins

            _after_commit(notify_admins, "No riders available",
                          f"Order {order.order_number} has no available riders near {order.restaurant_name}.",
                          {"order_id": str(order.id)})
        return None
    rider = candidates[0]
    ps = PlatformSettings.load()
    assignment = RiderAssignment.objects.create(
        order=order, rider=rider, distance_to_restaurant_km=q(rider.distance.km),
        expires_at=timezone.now() + timedelta(seconds=ps.rider_offer_timeout_seconds))
    _fanout(order, "rider.offered", rider_id=str(rider.id), assignment_id=str(assignment.id),
            expires_at=assignment.expires_at.isoformat())
    return assignment


@transaction.atomic
def rider_accept_offer(assignment_id, rider) -> Order:
    assignment = RiderAssignment.objects.select_for_update().select_related("order").get(pk=assignment_id)
    if assignment.rider_id != rider.id:
        raise BusinessError("Not your offer.", code="permission_denied", status_code=403)
    if assignment.status != AssignmentStatus.OFFERED:
        raise ConflictError("This offer is no longer available.", code="offer_unavailable")
    if assignment.expires_at < timezone.now():
        assignment.status = AssignmentStatus.EXPIRED
        assignment.save(update_fields=["status"])
        raise ConflictError("This offer has expired.", code="offer_expired")
    if not rider.can_take_orders:
        raise BusinessError("Go online to accept orders.", code="rider_offline")
    order = _lock(assignment.order_id)
    if order.rider_id and order.assignments.filter(status=AssignmentStatus.ACCEPTED).exists():
        raise ConflictError("Order already assigned to another rider.", code="offer_unavailable")
    if order.status not in (OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP):
        raise ConflictError("Order is no longer available for delivery.", code="offer_unavailable")
    assignment.status = AssignmentStatus.ACCEPTED
    assignment.responded_at = timezone.now()
    assignment.save(update_fields=["status", "responded_at"])
    order.rider = rider
    order.save(update_fields=["rider"])
    rider.is_on_delivery = True
    rider.save(update_fields=["is_on_delivery"])
    if order.status == OrderStatus.READY_FOR_PICKUP:
        transition(order, OrderStatus.RIDER_ASSIGNED, actor=rider.user)
    else:
        OrderStatusHistory.objects.create(order=order, from_status=order.status, to_status=order.status,
                                          changed_by=rider.user, actor_role=UserRole.RIDER, note="Rider assigned")
        _fanout(order, "rider.assigned")
    audit("order.rider_accepted", target=order, actor=rider.user)
    return order


@transaction.atomic
def rider_reject_offer(assignment_id, rider, reason="") -> None:
    assignment = RiderAssignment.objects.select_for_update().get(pk=assignment_id)
    if assignment.rider_id != rider.id:
        raise BusinessError("Not your offer.", code="permission_denied", status_code=403)
    if assignment.status != AssignmentStatus.OFFERED:
        return
    assignment.status = AssignmentStatus.REJECTED
    assignment.responded_at = timezone.now()
    assignment.rejection_reason = reason[:120]
    assignment.save(update_fields=["status", "responded_at", "rejection_reason"])
    from apps.riders.models import RiderProfile

    RiderProfile.objects.filter(pk=rider.pk).update(total_rejections=_f_plus("total_rejections", 1))
    from .tasks import dispatch_order

    _after_commit(dispatch_order.delay, str(assignment.order_id))


@transaction.atomic
def admin_assign_rider(order_id, rider, actor) -> Order:
    order = _lock(order_id)
    if order.status not in (OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
                            OrderStatus.RIDER_ASSIGNED):
        raise ConflictError("Order cannot be (re)assigned in its current state.", code="invalid_transition")
    _release_rider(order, reassigned=True)
    order.assignments.filter(status=AssignmentStatus.OFFERED).update(status=AssignmentStatus.CANCELLED)
    assignment = RiderAssignment.objects.create(order=order, rider=rider, is_manual=True,
                                                expires_at=timezone.now() + timedelta(minutes=10))
    assignment.status = AssignmentStatus.ACCEPTED
    assignment.responded_at = timezone.now()
    assignment.save()
    order.rider = rider
    order.save(update_fields=["rider"])
    rider.is_on_delivery = True
    rider.save(update_fields=["is_on_delivery"])
    if order.status == OrderStatus.RIDER_ASSIGNED:
        transition(order, OrderStatus.READY_FOR_PICKUP, actor=actor, note="Rider reassigned by admin")
    if order.status == OrderStatus.READY_FOR_PICKUP:
        transition(order, OrderStatus.RIDER_ASSIGNED, actor=actor, note="Assigned by admin")
    else:
        _fanout(order, "rider.assigned")
    audit("order.admin_assign_rider", target=order, actor=actor, rider_id=str(rider.id))
    return order


def _release_rider(order: Order, reassigned=False):
    if order.rider_id:
        from apps.riders.models import RiderProfile

        rider_id = order.rider_id
        rider_user_id = RiderProfile.objects.filter(pk=rider_id).values_list("user_id", flat=True).first()
        order.assignments.filter(status=AssignmentStatus.ACCEPTED).update(
            status=AssignmentStatus.REASSIGNED if reassigned else AssignmentStatus.CANCELLED)
        order.rider = None
        order.save(update_fields=["rider"])
        still_busy = Order.objects.filter(rider_id=rider_id, status__in=(
            OrderStatus.RIDER_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.NEAR_DESTINATION)).exclude(pk=order.pk).exists()
        if not still_busy:
            RiderProfile.objects.filter(pk=rider_id).update(is_on_delivery=False)
        from apps.notifications.services import notify_user

        if rider_user_id:
            _after_commit(notify_user, rider_user_id, "Order unassigned",
                          f"Order {order.order_number} was removed from your deliveries.",
                          {"type": "order.unassigned", "order_id": str(order.id)})
    order.assignments.filter(status=AssignmentStatus.OFFERED).update(status=AssignmentStatus.CANCELLED)


# ---------------------------------------------------------------- rider workflow

def _assert_rider(order: Order, rider):
    if order.rider_id != rider.id:
        raise BusinessError("This order is not assigned to you.", code="permission_denied", status_code=403)


@transaction.atomic
def rider_arrived_at_restaurant(order_id, rider) -> Order:
    order = _lock(order_id)
    _assert_rider(order, rider)
    OrderStatusHistory.objects.create(order=order, from_status=order.status, to_status=order.status,
                                      changed_by=rider.user, actor_role=UserRole.RIDER, note="Rider at restaurant")
    _fanout(order, "rider.at_restaurant")
    return order


def rider_pickup(order_id, rider, otp: str) -> Order:
    pre = Order.objects.only("id", "rider_id", "status").get(pk=order_id)
    _assert_rider(pre, rider)
    if pre.status != OrderStatus.RIDER_ASSIGNED:
        raise ConflictError("Food is not ready for pickup yet.", code="not_ready")
    _check_otp(order_id, "pickup", otp)
    return _rider_pickup_locked(order_id, rider)


@transaction.atomic
def _rider_pickup_locked(order_id, rider) -> Order:
    order = _lock(order_id)
    _assert_rider(order, rider)
    if order.status != OrderStatus.RIDER_ASSIGNED:
        raise ConflictError("Food is not ready for pickup yet.", code="not_ready")
    if order.otp_locked_at is not None:
        raise ConflictError("Order is locked.", code="otp_locked", status_code=423)
    transition(order, OrderStatus.PICKED_UP, actor=rider.user)
    transition(order, OrderStatus.OUT_FOR_DELIVERY, actor=rider.user)
    order.estimated_delivery_at = timezone.now() + timedelta(minutes=_travel_minutes(order))
    order.save(update_fields=["estimated_delivery_at"])
    return order


@transaction.atomic
def rider_near_destination(order_id) -> Order:
    order = _lock(order_id)
    if order.status == OrderStatus.OUT_FOR_DELIVERY:
        transition(order, OrderStatus.NEAR_DESTINATION, note="Rider within 300 m")
    return order


DELIVERABLE = (OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION, OrderStatus.PICKED_UP)


def rider_deliver(order_id, rider, otp: str, proof_file=None) -> Order:
    pre = Order.objects.only("id", "rider_id", "status").get(pk=order_id)
    _assert_rider(pre, rider)
    if pre.status not in DELIVERABLE:
        raise ConflictError("Order is not out for delivery.", code="invalid_transition")
    _check_otp(order_id, "delivery", otp)
    return _rider_deliver_locked(order_id, rider, proof_file)


@transaction.atomic
def _rider_deliver_locked(order_id, rider, proof_file=None) -> Order:
    order = _lock(order_id)
    _assert_rider(order, rider)
    if order.status not in DELIVERABLE:
        raise ConflictError("Order is not out for delivery.", code="invalid_transition")
    if order.otp_locked_at is not None:
        raise ConflictError("Order is locked.", code="otp_locked", status_code=423)
    if proof_file is not None:
        order.delivery_proof = proof_file
        order.save(update_fields=["delivery_proof"])
    transition(order, OrderStatus.DELIVERED, actor=rider.user)
    finalize_delivered_order(order)
    return order


@transaction.atomic
def rider_delivery_failed(order_id, rider, reason: str) -> Order:
    order = _lock(order_id)
    _assert_rider(order, rider)
    if order.status not in (OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION, OrderStatus.PICKED_UP):
        raise ConflictError("Order is not out for delivery.", code="invalid_transition")
    order.cancellation_reason = reason[:255]
    order.cancelled_by = CancelledBy.RIDER
    order.save(update_fields=["cancellation_reason", "cancelled_by"])
    transition(order, OrderStatus.DELIVERY_FAILED, actor=rider.user, note=reason)
    from apps.riders.models import RiderProfile

    RiderProfile.objects.filter(pk=rider.pk).update(is_on_delivery=False)
    from apps.notifications.services import notify_admins

    _after_commit(notify_admins, "Delivery failed", f"{order.order_number}: {reason}", {"order_id": str(order.id)})
    return order


def finalize_delivered_order(order: Order):
    """Money movement & stats after a successful delivery. Idempotent."""
    from apps.accounts.models import CustomerProfile
    from apps.finance.services import record_restaurant_settlement_line
    from apps.payments.services import mark_cod_collected
    from apps.restaurants.models import Restaurant
    from apps.riders.models import RiderProfile
    from apps.riders.services import credit_delivery_earning

    if order.payment_method == PaymentMethod.COD:
        mark_cod_collected(order)
    credit_delivery_earning(order)
    record_restaurant_settlement_line(order)
    RiderProfile.objects.filter(pk=order.rider_id).update(
        is_on_delivery=False, total_deliveries=_f_plus("total_deliveries", 1))
    Restaurant.objects.filter(pk=order.restaurant_id).update(total_orders=_f_plus("total_orders", 1))
    profile, _ = CustomerProfile.objects.get_or_create(user=order.customer)
    CustomerProfile.objects.filter(pk=profile.pk).update(total_orders=_f_plus("total_orders", 1))
    if profile.first_order_at is None:
        CustomerProfile.objects.filter(pk=profile.pk).update(first_order_at=timezone.now())
    for item in order.items.filter(menu_item__isnull=False):
        MenuItem.objects.filter(pk=item.menu_item_id).update(order_count=_f_plus("order_count", item.quantity))
    # still-busy check for batch deliveries
    busy = Order.objects.filter(rider_id=order.rider_id, status__in=(
        OrderStatus.RIDER_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY,
        OrderStatus.NEAR_DESTINATION)).exists()
    if busy:
        RiderProfile.objects.filter(pk=order.rider_id).update(is_on_delivery=True)


# ---------------------------------------------------------------- cancellation

@transaction.atomic
def cancel_order(order_id, actor, reason: str, by: str | None = None, force=False) -> Order:
    order = _lock(order_id)
    by = by or {UserRole.CUSTOMER: CancelledBy.CUSTOMER, UserRole.ADMIN: CancelledBy.ADMIN,
                UserRole.PARTNER: CancelledBy.RESTAURANT}.get(getattr(actor, "role", None), CancelledBy.SYSTEM)
    ps = PlatformSettings.load()
    fee = ZERO
    if by == CancelledBy.CUSTOMER:
        if order.customer_id != actor.id:
            raise BusinessError("Not your order.", code="permission_denied", status_code=403)
        if not order.customer_cancellable:
            raise ConflictError("This order can no longer be cancelled. Contact support.", code="not_cancellable")
        if order.status == OrderStatus.RESTAURANT_ACCEPTED:
            fee = min(ps.cancellation_fee_after_accept, order.total_amount)
    elif by == CancelledBy.RESTAURANT:
        _assert_restaurant_owner(order, actor)
        if order.status not in (OrderStatus.PLACED, OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING,
                                OrderStatus.READY_FOR_PICKUP, OrderStatus.RIDER_ASSIGNED):
            raise ConflictError("Order cannot be cancelled now.", code="not_cancellable")
    elif by == CancelledBy.ADMIN:
        if not (actor.role == UserRole.ADMIN or actor.is_staff):
            raise BusinessError("Admin only.", code="permission_denied", status_code=403)
        if order.is_terminal and not force:
            raise ConflictError("Order already finished.", code="not_cancellable")
    if order.status == OrderStatus.DELIVERED:
        raise ConflictError("Delivered orders cannot be cancelled; issue a refund instead.", code="not_cancellable")
    if order.status in (OrderStatus.CANCELLED, OrderStatus.REJECTED):
        raise ConflictError("Order is already cancelled.", code="not_cancellable")
    order.cancelled_by = by
    order.cancellation_reason = reason[:255]
    order.cancellation_fee = q(fee)
    order.save(update_fields=["cancelled_by", "cancellation_reason", "cancellation_fee"])
    transition(order, OrderStatus.CANCELLED, actor=actor, note=reason, fee=str(fee))
    _release_rider(order)
    _restock(order)
    refund_amount = q(order.total_amount - fee)
    _refund_if_paid(order, f"Order cancelled by {by}", actor, amount=refund_amount)
    audit("order.cancelled", target=order, actor=actor, by=by, reason=reason, fee=str(fee))
    return order


def _restock(order: Order):
    for item in order.items.filter(menu_item__isnull=False):
        MenuItem.objects.filter(pk=item.menu_item_id, stock_quantity__isnull=False).update(
            stock_quantity=_f_plus("stock_quantity", item.quantity))


def _refund_if_paid(order: Order, reason: str, actor=None, amount=None):
    if order.is_paid and order.payment_method in ONLINE_METHODS:
        from apps.payments.services import initiate_refund

        _after_commit(initiate_refund, str(order.id), reason, str(amount if amount is not None else order.total_amount),
                      getattr(actor, "id", None))


def reorder_to_cart(user, order: Order, replace=True):
    """Rebuild a cart from a past order (skipping unavailable items)."""
    from apps.cart.services import add_to_cart, get_cart

    if order.customer_id != user.id:
        raise BusinessError("Not your order.", code="permission_denied", status_code=403)
    cart = get_cart(user)
    if replace:
        cart.clear()
    skipped = []
    for item in order.items.select_related("menu_item"):
        mi = item.menu_item
        if mi is None or mi.deleted_at is not None or not mi.in_stock:
            skipped.append(item.name)
            continue
        variant_id = None
        if item.variant_name:
            v = mi.variant_groups.first()
            opt = v.options.filter(name=item.variant_name, is_available=True).first() if v else None
            if opt is None:
                skipped.append(item.name)
                continue
            variant_id = opt.id
        addon_ids = []
        for name in item.addon_names:
            from apps.menu.models import MenuAddon

            a = MenuAddon.objects.filter(group__item=mi, name=name, is_available=True).first()
            if a:
                addon_ids.append(a.id)
        try:
            add_to_cart(user, mi.id, item.quantity, variant_id, addon_ids, item.special_instructions,
                        replace_cart=True)
        except BusinessError:
            skipped.append(item.name)
    return get_cart(user), skipped
