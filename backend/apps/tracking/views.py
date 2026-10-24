from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import permissions, serializers, status
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from apps.core.exceptions import BusinessError
from apps.core.permissions import IsRider
from apps.orders.models import Order

from .services import ingest_location, send_chat_message, serialize_chat, tracking_snapshot


class LocationSerializer(serializers.Serializer):
    order_id = serializers.UUIDField(required=False, allow_null=True)
    latitude = serializers.FloatField()
    longitude = serializers.FloatField()
    accuracy = serializers.FloatField(required=False, default=0)
    speed = serializers.FloatField(required=False, default=0)
    bearing = serializers.FloatField(required=False, default=0)
    timestamp = serializers.CharField(required=False, allow_blank=True)


class LocationBatchSerializer(serializers.Serializer):
    locations = LocationSerializer(many=True)


@extend_schema(tags=["tracking"])
class RiderLocationView(APIView):
    """POST /api/v1/rider/location/ — single point or {"locations": [...]} batch (offline queue replay)."""

    permission_classes = [IsRider]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "location"

    @extend_schema(request=LocationSerializer, responses={200: dict})
    def post(self, request):
        from apps.riders.views import get_rider

        rider = get_rider(request)
        if "locations" in request.data:
            ser = LocationBatchSerializer(data=request.data)
            ser.is_valid(raise_exception=True)
            pts = sorted(ser.validated_data["locations"], key=lambda p: p.get("timestamp") or "")
            results = []
            for p in pts[-100:]:
                try:
                    results.append(ingest_location(rider.id, _plain(p)))
                except BusinessError as e:
                    results.append({"accepted": False, "reason": e.default_code or "error", "detail": str(e.detail)})
            return Response({"results": results})
        ser = LocationSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        return Response(ingest_location(rider.id, _plain(ser.validated_data)))


def _plain(d: dict) -> dict:
    return {k: (str(v) if k == "order_id" and v else v) for k, v in d.items()}


def _authorize_order_view(order: Order, user):
    if user.is_staff or user.role == "admin":
        return
    if order.customer_id == user.id or order.restaurant.owner_id == user.id:
        return
    if order.rider and order.rider.user_id == user.id:
        return
    raise BusinessError("Not allowed.", code="permission_denied", status_code=403)


@extend_schema(tags=["tracking"])
class OrderTrackingView(APIView):
    """REST fallback for the tracking screen (same payload as the WebSocket snapshot)."""

    permission_classes = [permissions.IsAuthenticated]

    @extend_schema(responses={200: dict})
    def get(self, request, order_id):
        order = Order.objects.select_related("restaurant", "rider").filter(pk=order_id).first()
        if order is None:
            raise BusinessError("Order not found.", code="not_found", status_code=404)
        _authorize_order_view(order, request.user)
        return Response(tracking_snapshot(order.id, request.user))


@extend_schema(tags=["tracking"])
class OrderChatView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request, order_id):
        order = Order.objects.select_related("restaurant", "rider").filter(pk=order_id).first()
        if order is None:
            raise BusinessError("Order not found.", code="not_found", status_code=404)
        _authorize_order_view(order, request.user)
        return Response([serialize_chat(m) for m in order.chat_messages.select_related("sender")[:200]])

    @extend_schema(request={"application/json": {"type": "object", "properties": {"text": {"type": "string"}}}})
    def post(self, request, order_id):
        text = (request.data.get("text") or "").strip()
        if not text:
            raise BusinessError("Message is empty.", code="validation_error")
        m = send_chat_message(order_id, request.user, text)
        return Response(serialize_chat(m), status=status.HTTP_201_CREATED)


@extend_schema(tags=["tracking"], parameters=[
    OpenApiParameter("from_lat", float, required=True), OpenApiParameter("from_lng", float, required=True),
    OpenApiParameter("to_lat", float, required=True), OpenApiParameter("to_lng", float, required=True),
    OpenApiParameter("steps", bool)])
class RouteView(APIView):
    """Generic routing proxy (self-hosted OSRM) used by the apps for previews."""

    permission_classes = [permissions.IsAuthenticated]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "geo"

    @extend_schema(responses={200: dict})
    def get(self, request):
        from apps.core.services.routing import routing_service

        try:
            pts = [(float(request.query_params["from_lat"]), float(request.query_params["from_lng"])),
                   (float(request.query_params["to_lat"]), float(request.query_params["to_lng"]))]
        except (KeyError, ValueError):
            raise BusinessError("from_lat, from_lng, to_lat, to_lng are required.", code="validation_error")
        steps = request.query_params.get("steps") in ("1", "true")
        return Response(routing_service.route(pts, steps=steps).as_dict(include_steps=steps))
