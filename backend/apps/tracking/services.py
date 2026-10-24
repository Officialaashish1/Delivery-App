"""
Location ingestion pipeline:
  rider app → (REST or WS) → validate → Redis latest → PostGIS profile (throttled) → history buffer
           → broadcast to order/admin groups → near-destination detection → route refresh when off-route
"""
import logging
from datetime import datetime, timedelta

from django.conf import settings
from django.utils import timezone
from django.utils.dateparse import parse_datetime

from apps.core.exceptions import BusinessError
from apps.core.geo import haversine_km, point_to_latlng
from apps.core.services.routing import decode_polyline, routing_service
from apps.orders.models import RIDER_ACTIVE_STATUSES, Order, OrderStatus

from . import store
from .broadcast import broadcast_chat, broadcast_rider_location

log = logging.getLogger("localrush.tracking")

NEAR_DESTINATION_M = 300
OFF_ROUTE_M = 120
ROUTE_REFRESH_SECONDS = 45
PROFILE_WRITE_INTERVAL_S = 10
HISTORY_SAMPLE_INTERVAL_S = 10


def _parse_ts(value) -> datetime:
    if not value:
        return timezone.now()
    if isinstance(value, int | float):
        return datetime.fromtimestamp(value / (1000 if value > 1e11 else 1), tz=timezone.utc)
    dt = parse_datetime(str(value))
    if dt is None:
        return timezone.now()
    if timezone.is_naive(dt):
        dt = timezone.make_aware(dt, timezone.utc)
    # clamp clock skew
    now = timezone.now()
    if dt > now + timedelta(seconds=30):
        dt = now
    if dt < now - timedelta(hours=6):
        dt = now
    return dt


def validate_point(lat, lng, accuracy):
    try:
        lat, lng = float(lat), float(lng)
    except (TypeError, ValueError):
        raise BusinessError("latitude/longitude must be numbers.", code="validation_error")
    if not (-90 <= lat <= 90 and -180 <= lng <= 180) or (lat == 0 and lng == 0):
        raise BusinessError("Invalid coordinates.", code="validation_error")
    if accuracy is not None and float(accuracy) > 500:
        raise BusinessError("GPS accuracy too low; update skipped.", code="low_accuracy")
    return lat, lng


def ingest_location(rider_id, payload: dict) -> dict:
    """Process one location update from a rider. Returns a small ack (used for backpressure)."""
    from apps.riders.models import RiderProfile

    rider = RiderProfile.objects.select_related("user").filter(pk=rider_id).first()
    if rider is None:
        raise BusinessError("Rider not found.", code="not_found", status_code=404)
    if not rider.is_online and not rider.is_on_delivery:
        return {"accepted": False, "reason": "offline"}
    lat, lng = validate_point(payload.get("latitude"), payload.get("longitude"), payload.get("accuracy"))
    recorded_at = _parse_ts(payload.get("timestamp"))
    speed = float(payload.get("speed") or 0)
    bearing = float(payload.get("bearing") or 0)
    accuracy = float(payload.get("accuracy") or 0)
    order_id = payload.get("order_id") or None

    # Plausibility: reject teleports (> LOCATION_MAX_SPEED_KMPH between consecutive points)
    prev = store.get_rider_location(rider_id)
    if prev:
        dt_s = max(1.0, (recorded_at - _parse_ts(prev["recorded_at"])).total_seconds())
        dist_km = haversine_km(prev["latitude"], prev["longitude"], lat, lng)
        if dist_km / dt_s * 3600 > settings.LOCALRUSH["LOCATION_MAX_SPEED_KMPH"] and dist_km > 0.2:
            log.warning("Implausible jump for rider %s: %.2f km in %.0fs", rider_id, dist_km, dt_s)
            return {"accepted": False, "reason": "implausible_speed"}
        if prev.get("recorded_at") and recorded_at <= _parse_ts(prev["recorded_at"]):
            return {"accepted": False, "reason": "stale"}

    loc = {"latitude": lat, "longitude": lng, "accuracy": accuracy, "speed": speed, "bearing": bearing,
           "recorded_at": recorded_at.isoformat(), "order_id": str(order_id) if order_id else None}
    store.set_rider_location(rider_id, loc)

    # Throttled durable write of the rider's current position (used by dispatch queries)
    if rider.location_updated_at is None or (timezone.now() - rider.location_updated_at).total_seconds() \
            >= PROFILE_WRITE_INTERVAL_S:
        rider.set_location(lat, lng, bearing, speed, at=timezone.now())

    # Which order (if any) does this update belong to? Only an order assigned to this rider and in transit.
    order = None
    active_q = Order.objects.filter(rider_id=rider_id, status__in=RIDER_ACTIVE_STATUSES)
    if order_id:
        order = active_q.filter(pk=order_id).first()
        if order is None:
            # rider claimed an order that is not theirs/active: still store for the rider, but do not broadcast.
            return {"accepted": True, "order_tracked": False}
    else:
        order = active_q.first()

    if order is not None:
        if (prev is None or prev.get("order_id") != str(order.id)
                or (recorded_at - _parse_ts(prev["recorded_at"])).total_seconds() >= HISTORY_SAMPLE_INTERVAL_S):
            store.push_history({"rider_id": str(rider_id), "order_id": str(order.id), "lat": lat, "lng": lng,
                                "accuracy": accuracy, "speed": speed, "bearing": bearing,
                                "recorded_at": recorded_at.isoformat()})
        route_update = maybe_refresh_route(order, lat, lng)
        broadcast_rider_location(order.id, rider_id, loc, route_update)
        if order.status == OrderStatus.OUT_FOR_DELIVERY:
            d_m = haversine_km(lat, lng, order.delivery_location.y, order.delivery_location.x) * 1000
            if d_m <= NEAR_DESTINATION_M:
                from apps.orders.services import rider_near_destination

                rider_near_destination(order.id)
        return {"accepted": True, "order_tracked": True, "order_id": str(order.id)}
    broadcast_rider_location(None, rider_id, loc)
    return {"accepted": True, "order_tracked": False}


def _destination_for(order: Order):
    if order.status in (OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION):
        return (order.delivery_location.y, order.delivery_location.x), "to_customer"
    return (order.restaurant_location.y, order.restaurant_location.x), "to_restaurant"


def _distance_to_polyline_m(lat, lng, geometry: str) -> float:
    pts = decode_polyline(geometry)
    if not pts:
        return 0.0
    return min(haversine_km(lat, lng, a, b) for a, b in pts[::max(1, len(pts) // 200)]) * 1000


def maybe_refresh_route(order: Order, lat: float, lng: float) -> dict | None:
    """Recompute the route if none cached, the leg changed, the rider is off-route, or it is stale."""
    cached = store.get_order_route(order.id)
    dest, leg = _destination_for(order)
    now = timezone.now()
    needs = cached is None or cached.get("leg") != leg
    if not needs:
        age = (now - _parse_ts(cached["computed_at"])).total_seconds()
        if age > ROUTE_REFRESH_SECONDS:
            needs = True
        elif _distance_to_polyline_m(lat, lng, cached["geometry"]) > OFF_ROUTE_M:
            needs = True
    if not needs:
        return None
    route = routing_service.route([(lat, lng), dest], use_cache=False)
    data = {**route.as_dict(), "leg": leg, "computed_at": now.isoformat(),
            "eta_at": (now + timedelta(seconds=route.duration_s)).isoformat()}
    store.set_order_route(order.id, data)
    if leg == "to_customer":
        Order.objects.filter(pk=order.pk).update(estimated_delivery_at=now + timedelta(seconds=route.duration_s))
    return data


def tracking_snapshot(order_id, user) -> dict:
    """Initial payload for the tracking screen (REST and WS share it)."""
    from apps.orders.serializers import OrderDetailSerializer, PartnerOrderSerializer, RiderOrderSerializer

    order = Order.objects.select_related("rider", "restaurant").prefetch_related("items", "status_history",
                                                                                 "refunds").get(pk=order_id)
    # Role-specific view of the order: partners never see the customer's phone; riders see delivery details only.
    if user is not None and not (user.is_staff or getattr(user, "role", "") == "admin"):
        if order.restaurant.owner_id == user.id and order.customer_id != user.id:
            order_data = PartnerOrderSerializer(order).data
        elif order.rider_id and order.rider.user_id == user.id and order.customer_id != user.id:
            order_data = RiderOrderSerializer(order).data
        else:
            order_data = OrderDetailSerializer(order).data
    else:
        order_data = OrderDetailSerializer(order).data
    data = {"order": order_data, "rider_location": None, "route": None,
            "restaurant_location": point_to_latlng(order.restaurant_location),
            "delivery_location": point_to_latlng(order.delivery_location)}
    if order.rider_id and order.status in RIDER_ACTIVE_STATUSES:
        loc = store.get_rider_location(order.rider_id)
        if loc is None and order.rider.current_location:
            loc = {"latitude": order.rider.current_location.y, "longitude": order.rider.current_location.x,
                   "bearing": order.rider.heading, "speed": order.rider.speed_kmph,
                   "recorded_at": order.rider.location_updated_at.isoformat()
                   if order.rider.location_updated_at else None}
        data["rider_location"] = loc
        route = store.get_order_route(order.id)
        if route is None and loc:
            route = maybe_refresh_route(order, loc["latitude"], loc["longitude"])
        data["route"] = route
    elif order.status in (OrderStatus.PLACED, OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING,
                          OrderStatus.READY_FOR_PICKUP):
        # Show the restaurant → customer path so the map is meaningful before a rider is assigned.
        key_route = store.get_order_route(f"{order.id}:static")
        if key_route is None:
            r = routing_service.route([(order.restaurant_location.y, order.restaurant_location.x),
                                       (order.delivery_location.y, order.delivery_location.x)])
            key_route = {**r.as_dict(), "leg": "restaurant_to_customer", "computed_at": timezone.now().isoformat()}
            store.set_order_route(f"{order.id}:static", key_route)
        data["route"] = key_route
    data["chat"] = [serialize_chat(m) for m in order.chat_messages.select_related("sender")[:100]]
    return data


def admin_live_snapshot() -> dict:
    from apps.orders.models import ACTIVE_STATUSES
    from apps.riders.models import RiderProfile

    riders = []
    for rp in RiderProfile.objects.filter(is_online=True).only("id", "full_name", "is_on_delivery",
                                                               "current_location", "heading"):
        loc = store.get_rider_location(rp.id)
        if loc is None and rp.current_location:
            loc = {"latitude": rp.current_location.y, "longitude": rp.current_location.x, "bearing": rp.heading}
        if loc:
            riders.append({"rider_id": str(rp.id), "name": rp.full_name, "on_delivery": rp.is_on_delivery, **loc})
    orders = list(Order.objects.filter(status__in=ACTIVE_STATUSES).values(
        "id", "order_number", "status", "restaurant_name", "rider_id", "total_amount", "placed_at"))
    return {"riders": riders, "active_orders": orders}


# ---------------------------------------------------------------- chat

def serialize_chat(m) -> dict:
    return {"id": str(m.id), "order_id": str(m.order_id), "sender_id": str(m.sender_id), "sender_role": m.sender_role,
            "text": m.text, "is_read": m.is_read, "created_at": m.created_at.isoformat()}


def _chat_participants(order: Order):
    return {order.customer_id, order.rider.user_id if order.rider else None}


def send_chat_message(order_id, sender, text: str):
    from .models import ChatMessage

    order = Order.objects.select_related("rider").get(pk=order_id)
    if sender.id not in _chat_participants(order) and not sender.is_staff:
        raise BusinessError("You are not part of this conversation.", code="permission_denied", status_code=403)
    if order.status not in RIDER_ACTIVE_STATUSES and order.status != OrderStatus.DELIVERED:
        raise BusinessError("Chat is available once a rider is assigned.", code="not_available")
    m = ChatMessage.objects.create(order=order, sender=sender, sender_role=sender.role, text=text[:500])
    payload = serialize_chat(m)
    broadcast_chat(order.id, payload)
    from apps.notifications.services import notify_user

    recipient = order.rider.user_id if sender.id == order.customer_id else order.customer_id
    if recipient:
        notify_user(recipient, "New message", text[:120], {"type": "chat.message", "order_id": str(order.id)},
                    push_only=True)
    return m


def mark_chat_read(order_id, user):
    from .models import ChatMessage

    ChatMessage.objects.filter(order_id=order_id).exclude(sender=user).update(is_read=True)
