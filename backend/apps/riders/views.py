from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import generics, mixins, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import NotFound
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from apps.core.exceptions import BusinessError
from apps.core.permissions import IsApprovedRider, IsRider
from apps.orders import serializers as order_s
from apps.orders.models import RIDER_ACTIVE_STATUSES, AssignmentStatus, Order, OrderStatus, RiderAssignment
from apps.orders.services import (
    rider_accept_offer,
    rider_arrived_at_restaurant,
    rider_deliver,
    rider_delivery_failed,
    rider_pickup,
    rider_reject_offer,
)

from . import serializers as s
from .models import RiderDocument, RiderEarning, RiderPayout, RiderProfile
from .services import earnings_summary, request_payout, set_online

# Orders the rider has accepted, including ones the restaurant is still preparing.
RIDER_VISIBLE_ACTIVE = RIDER_ACTIVE_STATUSES + (OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING,
                                                OrderStatus.READY_FOR_PICKUP)


def get_rider(request) -> RiderProfile:
    rp, _ = RiderProfile.objects.select_related("user").get_or_create(user=request.user,
                                                                      defaults={"full_name": request.user.full_name})
    return rp


@extend_schema(tags=["riders"])
class RiderProfileView(generics.RetrieveUpdateAPIView):
    permission_classes = [IsRider]
    serializer_class = s.RiderProfileSerializer

    def get_object(self):
        return get_rider(self.request)


@extend_schema(tags=["riders"])
class RiderDocumentViewSet(mixins.ListModelMixin, mixins.CreateModelMixin, mixins.DestroyModelMixin,
                           viewsets.GenericViewSet):
    permission_classes = [IsRider]
    serializer_class = s.RiderDocumentSerializer
    pagination_class = None

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return RiderDocument.objects.none()
        return RiderDocument.objects.filter(rider=get_rider(self.request))

    def perform_create(self, serializer):
        rider = get_rider(self.request)
        RiderDocument.objects.filter(rider=rider, doc_type=serializer.validated_data["doc_type"],
                                     status="rejected").delete()
        serializer.save(rider=rider)
        if rider.status == "rejected":
            RiderProfile.objects.filter(pk=rider.pk).update(status="pending")

    def perform_destroy(self, instance):
        if instance.status == "verified":
            raise BusinessError("Verified documents cannot be removed.", code="locked")
        instance.delete()


@extend_schema(tags=["riders"])
class RiderOnlineView(APIView):
    permission_classes = [IsRider]

    @extend_schema(request=s.OnlineToggleSerializer, responses=s.RiderProfileSerializer)
    def post(self, request):
        ser = s.OnlineToggleSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        rider = get_rider(request)
        rider = set_online(rider, d["is_online"])
        if d["is_online"] and d.get("latitude") is not None and d.get("longitude") is not None:
            rider.set_location(d["latitude"], d["longitude"])
        return Response(s.RiderProfileSerializer(rider, context={"request": request}).data)


@extend_schema(tags=["riders"], responses={200: dict})
class RiderDashboardView(APIView):
    permission_classes = [IsRider]

    def get(self, request):
        rider = get_rider(request)
        current = Order.objects.filter(rider=rider, status__in=RIDER_VISIBLE_ACTIVE).prefetch_related(
            "items").order_by("-accepted_at").first()
        pending_offers = RiderAssignment.objects.filter(rider=rider, status=AssignmentStatus.OFFERED).select_related(
            "order__restaurant").prefetch_related("order__items")
        from django.utils import timezone

        pending_offers = [a for a in pending_offers if a.expires_at > timezone.now()]
        data = earnings_summary(rider)
        data.update({
            "is_online": rider.is_online, "is_on_delivery": rider.is_on_delivery, "status": rider.status,
            "current_order": order_s.RiderOrderSerializer(current, context={"request": request}).data if current
            else None,
            "pending_offers": order_s.RiderOfferSerializer(pending_offers, many=True,
                                                           context={"request": request}).data,
        })
        return Response(data)


@extend_schema(tags=["riders"])
class RiderOfferViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    permission_classes = [IsApprovedRider]
    serializer_class = order_s.RiderOfferSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return RiderAssignment.objects.none()
        from django.utils import timezone

        return RiderAssignment.objects.filter(rider=get_rider(self.request), status=AssignmentStatus.OFFERED,
                                              expires_at__gt=timezone.now()).select_related(
            "order__restaurant").prefetch_related("order__items")

    @extend_schema(responses=order_s.RiderOrderSerializer)
    @action(detail=True, methods=["post"])
    def accept(self, request, pk=None):
        order = rider_accept_offer(pk, get_rider(request))
        return Response(order_s.RiderOrderSerializer(Order.objects.prefetch_related("items").get(pk=order.pk),
                                                     context={"request": request}).data)

    @extend_schema(request=s.RiderReasonSerializer, responses={204: None})
    @action(detail=True, methods=["post"])
    def reject(self, request, pk=None):
        rider_reject_offer(pk, get_rider(request), request.data.get("reason", ""))
        return Response(status=status.HTTP_204_NO_CONTENT)


@extend_schema(tags=["riders"])
class RiderOrderViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    permission_classes = [IsRider]
    serializer_class = order_s.RiderOrderSerializer
    throttle_scope = "rider_otp"  # applied only on the pickup/deliver actions (they add ScopedRateThrottle)

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return Order.objects.none()
        qs = Order.objects.filter(rider=get_rider(self.request)).select_related("restaurant").prefetch_related("items")
        scope = self.request.query_params.get("scope")
        if scope == "active":
            qs = qs.filter(status__in=RIDER_VISIBLE_ACTIVE)
        elif scope == "completed":
            qs = qs.filter(status=OrderStatus.DELIVERED)
        elif scope == "today":
            from django.utils import timezone

            qs = qs.filter(delivered_at__date=timezone.localdate())
        return qs

    @extend_schema(parameters=[OpenApiParameter("scope", str, description="active | completed | today")])
    def list(self, request, *args, **kwargs):
        return super().list(request, *args, **kwargs)

    def _respond(self, order):
        return Response(self.get_serializer(Order.objects.prefetch_related("items").get(pk=order.pk)).data)

    @action(detail=True, methods=["post"], url_path="arrived-restaurant")
    def arrived_restaurant(self, request, pk=None):
        return self._respond(rider_arrived_at_restaurant(self.get_object().id, get_rider(request)))

    @extend_schema(request=s.RiderOTPActionSerializer)
    @action(detail=True, methods=["post"], throttle_classes=[ScopedRateThrottle])
    def pickup(self, request, pk=None):
        ser = s.RiderOTPActionSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        return self._respond(rider_pickup(self.get_object().id, get_rider(request), ser.validated_data["otp"]))

    @extend_schema(request={"multipart/form-data": {"type": "object", "properties": {
        "otp": {"type": "string"}, "proof": {"type": "string", "format": "binary"}}}})
    @action(detail=True, methods=["post"], throttle_classes=[ScopedRateThrottle])
    def deliver(self, request, pk=None):
        ser = s.RiderOTPActionSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        proof = request.FILES.get("proof")
        if proof is not None:
            from apps.core.images import compress_image
            from apps.core.validators import validate_image_upload

            validate_image_upload(proof)
            proof = compress_image(proof, max_side=1024)
        return self._respond(rider_deliver(self.get_object().id, get_rider(request), ser.validated_data["otp"], proof))

    @extend_schema(request=s.RiderReasonSerializer)
    @action(detail=True, methods=["post"], url_path="delivery-failed")
    def delivery_failed(self, request, pk=None):
        ser = s.RiderReasonSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        return self._respond(rider_delivery_failed(self.get_object().id, get_rider(request),
                                                   ser.validated_data["reason"]))

    @extend_schema(responses={200: dict})
    @action(detail=True, methods=["get"], url_path="customer-contact")
    def customer_contact(self, request, pk=None):
        order = self.get_object()
        if order.status not in RIDER_ACTIVE_STATUSES:
            raise BusinessError("Customer contact is available only during delivery.", code="not_available")
        return Response({"phone": order.delivery_phone, "name": order.delivery_name})

    @extend_schema(parameters=[OpenApiParameter("lat", float), OpenApiParameter("lng", float)], responses={200: dict})
    @action(detail=True, methods=["get"])
    def route(self, request, pk=None):
        """Turn-by-turn route for the current leg (to restaurant before pickup, to customer after)."""
        from apps.core.services.routing import routing_service

        order = self.get_object()
        rider = get_rider(request)
        lat = request.query_params.get("lat")
        lng = request.query_params.get("lng")
        if lat and lng:
            try:
                origin = (float(lat), float(lng))
            except ValueError:
                raise BusinessError("Invalid lat/lng.", code="validation_error")
        elif rider.current_location:
            origin = (rider.current_location.y, rider.current_location.x)
        else:
            raise BusinessError("Current location required.", code="validation_error")
        if order.status in (OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION):
            dest = (order.delivery_location.y, order.delivery_location.x)
            leg = "to_customer"
        else:
            dest = (order.restaurant_location.y, order.restaurant_location.x)
            leg = "to_restaurant"
        route = routing_service.route([origin, dest], steps=True, use_cache=False)
        return Response({"leg": leg, **route.as_dict(include_steps=True)})


@extend_schema(tags=["riders"], responses={200: dict})
class RiderEarningsView(APIView):
    permission_classes = [IsRider]

    @extend_schema(parameters=[OpenApiParameter("period", str, description="today|week|month|all")])
    def get(self, request):
        from datetime import timedelta

        from django.utils import timezone

        rider = get_rider(request)
        period = request.query_params.get("period", "week")
        today = timezone.localdate()
        since = {"today": today, "week": today - timedelta(days=today.weekday()),
                 "month": today.replace(day=1)}.get(period)
        qs = RiderEarning.objects.filter(rider=rider).select_related("order")
        if since:
            qs = qs.filter(created_at__date__gte=since)
        summary = earnings_summary(rider)
        return Response({"summary": summary, "entries": s.RiderEarningSerializer(qs[:200], many=True).data})


@extend_schema(tags=["riders"])
class RiderPayoutViewSet(mixins.ListModelMixin, viewsets.GenericViewSet):
    permission_classes = [IsRider]
    serializer_class = s.RiderPayoutSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return RiderPayout.objects.none()
        return RiderPayout.objects.filter(rider=get_rider(self.request))

    @extend_schema(request=s.PayoutRequestSerializer, responses=s.RiderPayoutSerializer)
    @action(detail=False, methods=["post"], url_path="request")
    def request_payout(self, request):
        ser = s.PayoutRequestSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        payout = request_payout(get_rider(request), ser.validated_data.get("amount"))
        return Response(s.RiderPayoutSerializer(payout).data, status=status.HTTP_201_CREATED)


@extend_schema(tags=["riders"])
class RiderHistoryDetailView(generics.RetrieveAPIView):
    permission_classes = [IsRider]
    serializer_class = order_s.RiderOrderSerializer

    def get_object(self):
        o = Order.objects.filter(rider=get_rider(self.request), pk=self.kwargs["pk"]).first()
        if o is None:
            raise NotFound()
        return o
