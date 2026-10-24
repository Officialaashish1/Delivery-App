"""
All notification copy lives here so the three apps and the dashboard stay consistent.
notify_order_event() is called by order services after commit with an event string such as
'status.preparing', 'rider.offered', 'order.placed', 'refund.initiated'.
"""
import logging

from django.db import IntegrityError

from apps.accounts.models import User, UserRole

from .models import Notification, NotificationCategory

log = logging.getLogger("localrush.notifications")


def notify_user(user_id, title: str, body: str, data: dict | None = None, category=NotificationCategory.ORDER,
                dedupe_key: str = "", push_only=False, channel_id="localrush_orders"):
    if not user_id:
        return None
    from .tasks import send_push

    notification = None
    if not push_only:
        try:
            notification = Notification.objects.create(user_id=user_id, category=category, title=title[:120],
                                                       body=body[:500], data=data or {}, dedupe_key=dedupe_key[:120])
        except IntegrityError:
            return None  # duplicate (idempotent)
    send_push.delay(str(user_id), title, body, data or {}, str(notification.id) if notification else None, channel_id)
    return notification


def notify_admins(title: str, body: str, data: dict | None = None):
    for uid in User.objects.filter(role=UserRole.ADMIN, is_active=True).values_list("id", flat=True):
        notify_user(uid, title, body, data, category=NotificationCategory.SYSTEM)


def notify_order_event(order_id, event: str, extra: dict | None = None):
    from apps.orders.models import Order

    order = Order.objects.select_related("restaurant", "rider", "customer").filter(pk=order_id).first()
    if order is None:
        return
    extra = extra or {}
    n = order.order_number
    base_data = {"type": event, "order_id": str(order.id), "order_number": n, "status": order.status}
    key = f"{order.id}:{event}"
    cust = order.customer_id
    owner = order.restaurant.owner_id
    rider_uid = order.rider.user_id if order.rider_id else None

    customer_msgs = {
        "status.placed": ("Order placed 🎉", f"{order.restaurant_name} has received your order {n}."),
        "order.placed": None,
        "status.restaurant_accepted": ("Order accepted ✅", f"{order.restaurant_name} accepted your order and will start cooking."),
        "status.preparing": ("Cooking started 🍳", f"{order.restaurant_name} is preparing your food."),
        "status.ready_for_pickup": ("Food is ready", "Your food is packed and waiting for a delivery partner."),
        "rider.assigned": ("Rider assigned 🛵", f"{order.rider.full_name if order.rider else 'A rider'} will deliver your order."),
        "status.rider_assigned": ("Rider assigned 🛵", f"{order.rider.full_name if order.rider else 'A rider'} is heading to the restaurant."),
        "status.picked_up": ("Order picked up", "Your rider has picked up the order."),
        "status.out_for_delivery": ("On the way 🛵", f"Your order {n} is on its way."),
        "status.near_destination": ("Rider is nearby", "Your rider is almost at your doorstep. Keep the OTP ready."),
        "status.delivered": ("Delivered ✅", "Enjoy your meal! Tap to rate your experience."),
        "status.rejected": ("Order rejected", f"{order.restaurant_name} could not accept your order. Any payment will be refunded."),
        "status.cancelled": ("Order cancelled", f"Order {n} has been cancelled."),
        "status.payment_failed": ("Payment failed", "Your payment did not go through. Tap to retry."),
        "status.delivery_failed": ("Delivery unsuccessful", "We could not complete the delivery. Support will contact you."),
        "refund.initiated": ("Refund initiated", f"₹{extra.get('amount', '')} refund for {n} has been initiated. It may take 5-7 days."),
        "refund.completed": ("Refund completed", f"₹{extra.get('amount', '')} has been refunded for order {n}."),
        "refund.failed": ("Refund issue", f"Refund for {n} needs attention. Support will reach out."),
    }
    partner_msgs = {
        "status.placed": ("🔔 New order!", f"{n} · ₹{order.total_amount} · {order.items.count()} items"),
        "rider.assigned": ("Rider assigned", f"{order.rider.full_name if order.rider else 'Rider'} will pick up {n}."),
        "status.rider_assigned": ("Rider assigned", f"{order.rider.full_name if order.rider else 'Rider'} will pick up {n}."),
        "rider.at_restaurant": ("Rider arrived", f"Rider is at your counter for {n}. Pickup code: {order.pickup_otp}"),
        "status.picked_up": ("Order picked up", f"{n} has left your restaurant."),
        "status.delivered": ("Delivered", f"{n} delivered to the customer."),
        "status.cancelled": ("Order cancelled", f"{n} was cancelled ({order.cancelled_by})."),
        "status.delivery_failed": ("Delivery failed", f"{n} could not be delivered."),
    }
    rider_msgs = {
        "rider.offered": ("New delivery request 🛵", f"{order.restaurant_name} → {order.delivery_address_text[:40]} · ₹{order.rider_payout}"),
        "status.ready_for_pickup": ("Food is ready", f"{n} is ready at {order.restaurant_name}."),
        "status.rider_assigned": ("Pickup ready", f"{n} is ready for pickup at {order.restaurant_name}."),
        "status.cancelled": ("Order cancelled", f"{n} was cancelled."),
        "status.delivered": ("Delivery complete", f"₹{order.rider_payout} added to your earnings."),
    }
    if event in customer_msgs and customer_msgs[event]:
        t, b = customer_msgs[event]
        cat = NotificationCategory.PAYMENT if event.startswith("refund") or "payment" in event else NotificationCategory.ORDER
        notify_user(cust, t, b, base_data, category=cat, dedupe_key=key)
    if event in partner_msgs:
        t, b = partner_msgs[event]
        notify_user(owner, t, b, base_data, dedupe_key=key, channel_id="localrush_partner_orders")
    if event == "rider.offered":
        from apps.riders.models import RiderProfile

        rid = extra.get("rider_id")
        uid = RiderProfile.objects.filter(pk=rid).values_list("user_id", flat=True).first() if rid else None
        if uid:
            t, b = rider_msgs[event]
            notify_user(uid, t, b, {**base_data, "assignment_id": extra.get("assignment_id", ""),
                                    "expires_at": extra.get("expires_at", "")},
                        dedupe_key=f"{key}:{rid}", channel_id="localrush_rider_offers")
    elif event in rider_msgs and rider_uid:
        t, b = rider_msgs[event]
        notify_user(rider_uid, t, b, base_data, dedupe_key=key, channel_id="localrush_rider_orders")
