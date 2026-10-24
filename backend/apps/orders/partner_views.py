from django.utils import timezone
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import mixins, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from apps.core.permissions import IsPartner
from apps.restaurants.views import get_own_restaurant

from . import serializers as s
from .models import ACTIVE_STATUSES, Order, OrderStatus
from .services import (
    cancel_order,
    restaurant_accept,
    restaurant_preparing,
    restaurant_ready,
    restaurant_reject,
)


@extend_schema(tags=["partner"])
class PartnerOrderViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    permission_classes = [IsPartner]
    serializer_class = s.PartnerOrderSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return Order.objects.none()
        r = get_own_restaurant(self.request)
        qs = Order.objects.filter(restaurant=r).exclude(status=OrderStatus.PENDING_PAYMENT).select_related(
            "rider").prefetch_related("items")
        scope = self.request.query_params.get("scope")
        if scope == "new":
            qs = qs.filter(status=OrderStatus.PLACED)
        elif scope == "active":
            qs = qs.filter(status__in=ACTIVE_STATUSES)
        elif scope == "preparing":
            qs = qs.filter(status__in=(OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING))
        elif scope == "ready":
            qs = qs.filter(status__in=(OrderStatus.READY_FOR_PICKUP, OrderStatus.RIDER_ASSIGNED))
        elif scope == "dispatched":
            qs = qs.filter(status__in=(OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY,
                                       OrderStatus.NEAR_DESTINATION))
        elif scope == "today":
            qs = qs.filter(created_at__date=timezone.localdate())
        elif scope == "past":
            qs = qs.exclude(status__in=ACTIVE_STATUSES)
        st = self.request.query_params.get("status")
        if st:
            qs = qs.filter(status=st)
        return qs

    @extend_schema(parameters=[OpenApiParameter("scope", str, description="new|active|preparing|ready|dispatched|today|past"),
                               OpenApiParameter("status", str)])
    def list(self, request, *args, **kwargs):
        return super().list(request, *args, **kwargs)

    def _respond(self, order):
        return Response(self.get_serializer(self.get_queryset().get(pk=order.pk)).data)

    @extend_schema(request=s.AcceptOrderSerializer)
    @action(detail=True, methods=["post"])
    def accept(self, request, pk=None):
        ser = s.AcceptOrderSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        return self._respond(restaurant_accept(self.get_object().id, request.user,
                                               ser.validated_data.get("preparation_minutes")))

    @extend_schema(request=s.RejectOrderSerializer)
    @action(detail=True, methods=["post"])
    def reject(self, request, pk=None):
        ser = s.RejectOrderSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        return self._respond(restaurant_reject(self.get_object().id, request.user, ser.validated_data["reason"]))

    @action(detail=True, methods=["post"])
    def preparing(self, request, pk=None):
        return self._respond(restaurant_preparing(self.get_object().id, request.user))

    @action(detail=True, methods=["post"])
    def ready(self, request, pk=None):
        return self._respond(restaurant_ready(self.get_object().id, request.user))

    @extend_schema(request=s.RejectOrderSerializer)
    @action(detail=True, methods=["post"])
    def cancel(self, request, pk=None):
        ser = s.RejectOrderSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        return self._respond(cancel_order(self.get_object().id, request.user, ser.validated_data["reason"]))
