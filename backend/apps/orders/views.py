from django.http import HttpResponse
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import mixins, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from apps.accounts.models import Address
from apps.core.exceptions import BusinessError
from apps.core.permissions import IsCustomer

from . import serializers as s
from .models import ACTIVE_STATUSES, Order, OrderStatus
from .services import cancel_order, place_order, reorder_to_cart


@extend_schema(tags=["orders"])
class OrderViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """Customer order endpoints."""

    permission_classes = [IsCustomer]

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return Order.objects.none()
        qs = Order.objects.filter(customer=self.request.user).select_related("restaurant", "rider").prefetch_related(
            "items", "status_history", "refunds")
        scope = self.request.query_params.get("scope")
        customer_active = ACTIVE_STATUSES + (OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED)
        if scope == "active":
            qs = qs.filter(status__in=customer_active)
        elif scope == "past":
            qs = qs.exclude(status__in=customer_active)
        return qs

    def get_serializer_class(self):
        return s.OrderListSerializer if self.action == "list" else s.OrderDetailSerializer

    @extend_schema(parameters=[OpenApiParameter("scope", str, description="active | past")])
    def list(self, request, *args, **kwargs):
        return super().list(request, *args, **kwargs)

    @extend_schema(request=s.PlaceOrderSerializer, responses=s.PlaceOrderResponseSerializer)
    def create(self, request):
        ser = s.PlaceOrderSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        address = Address.objects.filter(id=d["address_id"], user=request.user).first()
        if address is None:
            raise BusinessError("Address not found.", code="not_found", status_code=404)
        order = place_order(request.user, address=address, payment_method=d["payment_method"],
                            idempotency_key=d.get("idempotency_key") or None, is_contactless=d["is_contactless"],
                            delivery_instructions=d["delivery_instructions"], customer_note=d["customer_note"])
        from apps.payments.services import client_payment_payload

        order = self.get_queryset().get(pk=order.pk)
        return Response({"order": s.OrderDetailSerializer(order, context={"request": request}).data,
                         "payment": client_payment_payload(order)}, status=status.HTTP_201_CREATED)

    @extend_schema(request=s.CancelOrderSerializer, responses=s.OrderDetailSerializer)
    @action(detail=True, methods=["post"])
    def cancel(self, request, pk=None):
        ser = s.CancelOrderSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        order = self.get_object()
        cancel_order(order.id, request.user, ser.validated_data["reason"])
        return Response(s.OrderDetailSerializer(self.get_queryset().get(pk=order.pk),
                                                context={"request": request}).data)

    @extend_schema(responses={200: dict})
    @action(detail=True, methods=["post"])
    def reorder(self, request, pk=None):
        from apps.cart.serializers import CartSerializer

        order = self.get_object()
        cart, skipped = reorder_to_cart(request.user, order)
        return Response({"cart": CartSerializer(cart, context={"request": request}).data, "skipped_items": skipped})

    @extend_schema(responses={200: {"type": "string", "format": "binary"}})
    @action(detail=True, methods=["get"])
    def invoice(self, request, pk=None):
        from .invoice import render_invoice_html

        order = self.get_object()
        html = render_invoice_html(order)
        resp = HttpResponse(html, content_type="text/html; charset=utf-8")
        resp["Content-Disposition"] = f'inline; filename="invoice-{order.order_number}.html"'
        return resp

    @extend_schema(responses={200: dict})
    @action(detail=True, methods=["get"], url_path="rider-contact")
    def rider_contact(self, request, pk=None):
        """Returns the rider's phone only while the delivery is in progress."""
        order = self.get_object()
        from .models import RIDER_ACTIVE_STATUSES

        if order.rider is None or order.status not in RIDER_ACTIVE_STATUSES:
            raise BusinessError("Rider contact is available only during delivery.", code="not_available")
        return Response({"phone": order.rider.user.phone, "name": order.rider.full_name})
