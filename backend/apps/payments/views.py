import json
import logging

from django.views.decorators.csrf import csrf_exempt
from drf_spectacular.utils import extend_schema
from rest_framework import permissions, serializers, status
from rest_framework.decorators import api_view, authentication_classes, permission_classes
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from apps.core.exceptions import BusinessError
from apps.core.permissions import IsCustomer
from apps.orders.models import Order
from apps.orders.serializers import OrderDetailSerializer

from .models import Payment, Refund
from .providers import get_provider
from .services import process_webhook, retry_payment, verify_checkout

log = logging.getLogger("localrush.payments")


class VerifySerializer(serializers.Serializer):
    order_id = serializers.UUIDField()
    razorpay_order_id = serializers.CharField()
    razorpay_payment_id = serializers.CharField()
    razorpay_signature = serializers.CharField()


class PaymentSerializer(serializers.ModelSerializer):
    class Meta:
        model = Payment
        fields = ("id", "provider", "method", "amount", "currency", "status", "provider_order_id",
                  "provider_payment_id", "failure_reason", "captured_at", "created_at")


class RefundSerializer(serializers.ModelSerializer):
    order_number = serializers.CharField(source="order.order_number", read_only=True)

    class Meta:
        model = Refund
        fields = ("id", "order", "order_number", "amount", "reason", "status", "processed_at", "created_at")


@extend_schema(tags=["payments"])
class VerifyPaymentView(APIView):
    permission_classes = [IsCustomer]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "payment"

    @extend_schema(request=VerifySerializer, responses=OrderDetailSerializer)
    def post(self, request):
        ser = VerifySerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        order = Order.objects.filter(id=d["order_id"], customer=request.user).first()
        if order is None:
            raise BusinessError("Order not found.", code="not_found", status_code=404)
        order = verify_checkout(order, d["razorpay_order_id"], d["razorpay_payment_id"], d["razorpay_signature"])
        order = Order.objects.prefetch_related("items", "status_history").get(pk=order.pk)
        return Response(OrderDetailSerializer(order, context={"request": request}).data)


@extend_schema(tags=["payments"])
class RetryPaymentView(APIView):
    permission_classes = [IsCustomer]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "payment"

    @extend_schema(responses={200: dict})
    def post(self, request, order_id):
        order = Order.objects.filter(id=order_id, customer=request.user).first()
        if order is None:
            raise BusinessError("Order not found.", code="not_found", status_code=404)
        return Response({"payment": retry_payment(order)})


@extend_schema(tags=["payments"])
class OrderPaymentsView(APIView):
    permission_classes = [IsCustomer]

    def get(self, request, order_id):
        order = Order.objects.filter(id=order_id, customer=request.user).first()
        if order is None:
            raise BusinessError("Order not found.", code="not_found", status_code=404)
        return Response({"payments": PaymentSerializer(order.payments.all(), many=True).data,
                         "refunds": RefundSerializer(order.refunds.all(), many=True).data})


@extend_schema(tags=["payments"])
class MyRefundsView(APIView):
    permission_classes = [IsCustomer]

    def get(self, request):
        return Response(RefundSerializer(Refund.objects.filter(order__customer=request.user)[:50], many=True).data)


@csrf_exempt
@api_view(["POST"])
@authentication_classes([])
@permission_classes([permissions.AllowAny])
def razorpay_webhook(request):
    """Razorpay → LocalRush. Signature verified with RAZORPAY_WEBHOOK_SECRET; events are idempotent."""
    provider = get_provider()
    signature = request.headers.get("X-Razorpay-Signature", "")
    if not provider.verify_webhook_signature(request.body, signature):
        log.warning("webhook signature invalid")
        return Response({"error": "invalid signature"}, status=status.HTTP_400_BAD_REQUEST)
    try:
        payload = json.loads(request.body)
    except json.JSONDecodeError:
        return Response({"error": "bad json"}, status=400)
    event_id = request.headers.get("X-Razorpay-Event-Id") or payload.get("id") or ""
    event_type = payload.get("event", "")
    if not event_id:
        import hashlib

        event_id = hashlib.sha256(request.body).hexdigest()
    try:
        process_webhook(provider.name, event_id, event_type, payload)
    except Exception:  # noqa: BLE001
        # Return 200 so Razorpay does not hammer us; the event is persisted with its error for admin replay.
        return Response({"status": "logged"})
    return Response({"status": "ok"})
