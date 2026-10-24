"""Channel-layer fan-out helpers. Group names:
   order_<id>       customer/admin/partner watching one order
   rider_<rider_id> the rider's own app (offers, unassignment)
   restaurant_<id>  the partner app (new orders, rider arrival)
   admin_live       admin dashboards (all active riders)
"""
import logging

from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer

log = logging.getLogger("localrush.tracking")


def _send(group: str, payload: dict):
    layer = get_channel_layer()
    if layer is None:
        return
    try:
        async_to_sync(layer.group_send)(group, {"type": "push.event", "payload": payload})
    except Exception:  # noqa: BLE001
        log.exception("group_send failed for %s", group)


def order_group(order_id):
    return f"order_{order_id}"


def rider_group(rider_id):
    return f"rider_{rider_id}"


def restaurant_group(restaurant_id):
    return f"restaurant_{restaurant_id}"


ADMIN_GROUP = "admin_live"


def broadcast_order_update(order_id, event: str, extra: dict | None = None):
    from apps.orders.models import Order
    from apps.orders.serializers import build_timeline

    order = Order.objects.select_related("rider", "restaurant").prefetch_related("status_history").filter(
        pk=order_id).first()
    if order is None:
        return
    base = {
        "type": event, "order_id": str(order.id), "order_number": order.order_number, "status": order.status,
        "status_display": order.get_status_display(),
        "estimated_delivery_at": order.estimated_delivery_at.isoformat() if order.estimated_delivery_at else None,
        "rider": None, "timeline": [{**t, "at": t["at"].isoformat() if t["at"] else None}
                                    for t in build_timeline(order)],
        **(extra or {}),
    }
    if order.rider_id:
        base["rider"] = {"id": str(order.rider_id), "full_name": order.rider.full_name,
                         "rating_avg": str(order.rider.rating_avg), "vehicle_number": order.rider.vehicle_number,
                         "photo": order.rider.photo.url if order.rider.photo else None}
    _send(order_group(order.id), base)
    _send(restaurant_group(order.restaurant_id), base)
    if event.startswith("rider.offered"):
        _send(rider_group(extra["rider_id"]), base)
    elif order.rider_id:
        _send(rider_group(order.rider_id), base)
    _send(ADMIN_GROUP, {**base, "restaurant_id": str(order.restaurant_id)})


def broadcast_rider_location(order_id, rider_id, loc: dict, route_update: dict | None = None):
    payload = {"type": "rider.location", "order_id": str(order_id) if order_id else None, "rider_id": str(rider_id),
               **loc}
    if route_update:
        payload["route"] = route_update
    if order_id:
        _send(order_group(order_id), payload)
    _send(ADMIN_GROUP, payload)


def broadcast_chat(order_id, message: dict):
    _send(order_group(order_id), {"type": "chat.message", **message})
