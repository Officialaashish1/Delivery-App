import logging
from decimal import Decimal

from django.db import transaction
from django.utils import timezone

from apps.core.audit import audit
from apps.core.exceptions import BusinessError, ConflictError
from apps.core.money import q
from apps.orders.models import ONLINE_METHODS, Order, OrderStatus, PaymentMethod

from .models import Payment, PaymentStatus, Refund, RefundStatus, WebhookEvent
from .providers import ProviderError, get_provider

log = logging.getLogger("localrush.payments")


def create_payment_for_order(order: Order) -> Payment:
    if order.payment_method == PaymentMethod.COD:
        return Payment.objects.create(order=order, user=order.customer, provider="cod", method=PaymentMethod.COD,
                                      amount=order.total_amount, status=PaymentStatus.COD_PENDING)
    provider = get_provider()
    payment = Payment.objects.create(order=order, user=order.customer, provider=provider.name,
                                     method=order.payment_method, amount=order.total_amount)
    try:
        res = provider.create_order(order.total_amount, "INR", order.order_number,
                                    {"order_id": str(order.id), "customer": order.customer.phone})
    except ProviderError as e:
        raise BusinessError(str(e), code="payment_provider_unavailable", status_code=503)
    except Exception as e:  # noqa: BLE001
        log.exception("provider order creation failed")
        raise BusinessError("Payment service unavailable, please try again.", code="payment_provider_unavailable",
                            status_code=503) from e
    payment.provider_order_id = res["provider_order_id"]
    payment.raw_response = res["raw"]
    payment.save(update_fields=["provider_order_id", "raw_response"])
    return payment


def client_payment_payload(order: Order) -> dict | None:
    if order.payment_method == PaymentMethod.COD:
        return None
    payment = order.payments.filter(status=PaymentStatus.CREATED).order_by("-created_at").first()
    if payment is None:
        return None
    return get_provider().checkout_payload(payment, order)


@transaction.atomic
def retry_payment(order: Order) -> dict:
    """Create a fresh provider order for an order whose previous attempt failed."""
    order = Order.objects.select_for_update().get(pk=order.pk)
    if order.status not in (OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED):
        raise ConflictError("Order is not awaiting payment.", code="invalid_transition")
    if order.status == OrderStatus.PAYMENT_FAILED:
        from apps.orders.models import OrderStatusHistory

        OrderStatusHistory.objects.create(order=order, from_status=order.status,
                                          to_status=OrderStatus.PENDING_PAYMENT, note="Payment retry")
        order.status = OrderStatus.PENDING_PAYMENT
        order.save(update_fields=["status"])
    order.payments.filter(status=PaymentStatus.CREATED).update(status=PaymentStatus.FAILED,
                                                               failure_reason="superseded")
    create_payment_for_order(order)
    return client_payment_payload(order)


@transaction.atomic
def verify_checkout(order: Order, provider_order_id: str, provider_payment_id: str, signature: str) -> Order:
    """Called by the app after Razorpay Checkout succeeds. Webhook is the authoritative backup."""

    provider = get_provider()
    payment = Payment.objects.select_for_update().filter(order=order, provider_order_id=provider_order_id).first()
    if payment is None:
        raise BusinessError("Unknown payment reference.", code="payment_not_found", status_code=404)
    if payment.status == PaymentStatus.CAPTURED:
        return order
    if not provider.verify_checkout_signature(provider_order_id, provider_payment_id, signature):
        payment.status = PaymentStatus.FAILED
        payment.failure_reason = "signature mismatch"
        payment.save(update_fields=["status", "failure_reason"])
        audit("payment.signature_mismatch", target=payment)
        raise BusinessError("Payment verification failed.", code="payment_invalid_signature")
    # Fetch from provider to confirm capture state + amount (defence in depth)
    try:
        remote = provider.fetch_payment(provider_payment_id)
    except Exception:  # noqa: BLE001
        log.exception("fetch_payment failed; relying on signature + webhook")
        remote = {"status": "captured", "amount": int(payment.amount * 100)}
    if remote.get("status") not in ("captured", "authorized"):
        raise BusinessError("Payment not completed.", code="payment_not_captured")
    if int(remote.get("amount", 0)) != int(payment.amount * 100):
        audit("payment.amount_mismatch", target=payment, remote=remote.get("amount"))
        raise BusinessError("Payment amount mismatch.", code="payment_amount_mismatch")
    _mark_captured(payment, provider_payment_id, signature, remote)
    return _apply_capture(payment)


def _apply_capture(payment: Payment) -> Order:
    """Confirm the order for a captured payment. If the order is already paid (duplicate capture after a retry) or no
    longer awaiting payment (cancelled while Checkout was open), refund THIS payment instead of keeping the money."""
    from apps.orders.services import PaymentNotApplicable, confirm_online_payment

    try:
        return confirm_online_payment(payment.order_id)
    except PaymentNotApplicable:
        order = Order.objects.get(pk=payment.order_id)
        audit("payment.capture_not_applicable", target=payment, order_status=order.status, already_paid=order.is_paid)
        transaction.on_commit(lambda: initiate_refund(str(order.id), "Duplicate or late payment capture",
                                                      str(payment.amount), payment_id=str(payment.id)))
        return order


def _mark_captured(payment: Payment, provider_payment_id: str, signature: str, raw: dict):
    payment.provider_payment_id = provider_payment_id
    payment.provider_signature = signature or ""
    payment.status = PaymentStatus.CAPTURED
    payment.captured_at = timezone.now()
    payment.raw_response = raw
    payment.save(update_fields=["provider_payment_id", "provider_signature", "status", "captured_at",
                                "raw_response"])
    audit("payment.captured", target=payment, amount=str(payment.amount))


def process_webhook(provider_name: str, event_id: str, event_type: str, payload: dict) -> bool:
    """Idempotent webhook processing. The WebhookEvent row is committed first (outside the processing transaction)
    so a failure is recorded for admin replay instead of being rolled back. Returns True if newly processed."""
    event, created = WebhookEvent.objects.get_or_create(
        provider=provider_name, event_id=event_id, defaults={"event_type": event_type, "payload": payload})
    if not created and event.processed:
        return False
    try:
        with transaction.atomic():
            _process_webhook_event(event_type, payload)
    except Exception as e:  # noqa: BLE001
        log.exception("webhook processing failed")
        WebhookEvent.objects.filter(pk=event.pk).update(error=str(e)[:2000])
        raise
    WebhookEvent.objects.filter(pk=event.pk).update(processed=True, error="")
    return True


def _process_webhook_event(event_type: str, payload: dict):
    from apps.orders.services import fail_online_payment

    entity = payload.get("payload", {}).get("payment", {}).get("entity", {})
    refund_entity = payload.get("payload", {}).get("refund", {}).get("entity", {})
    if event_type in ("payment.captured", "payment.authorized", "order.paid"):
        provider_order_id = entity.get("order_id")
        payment = Payment.objects.select_for_update().filter(provider_order_id=provider_order_id).first()
        if payment is None:
            raise ValueError(f"no payment for provider order {provider_order_id}")
        if payment.status != PaymentStatus.CAPTURED:
            if int(entity.get("amount", 0)) != int(payment.amount * 100):
                raise ValueError("amount mismatch")
            _mark_captured(payment, entity.get("id", ""), "", entity)
            _apply_capture(payment)
    elif event_type == "payment.failed":
        provider_order_id = entity.get("order_id")
        payment = Payment.objects.select_for_update().filter(provider_order_id=provider_order_id).first()
        if payment and payment.status == PaymentStatus.CREATED:
            payment.status = PaymentStatus.FAILED
            payment.failure_reason = (entity.get("error_description") or "failed")[:255]
            payment.raw_response = entity
            payment.save(update_fields=["status", "failure_reason", "raw_response"])
            fail_online_payment(payment.order_id, payment.failure_reason)
    elif event_type in ("refund.processed", "refund.failed"):
        refund = Refund.objects.select_for_update().filter(provider_refund_id=refund_entity.get("id")).first()
        if refund:
            refund.status = RefundStatus.COMPLETED if event_type == "refund.processed" else RefundStatus.FAILED
            refund.processed_at = timezone.now()
            refund.raw_response = refund_entity
            refund.save(update_fields=["status", "processed_at", "raw_response"])
            _sync_payment_refund_state(refund)
            from apps.notifications.services import notify_order_event

            notify_order_event(refund.order_id, "refund.completed" if refund.status == RefundStatus.COMPLETED
                               else "refund.failed", {"amount": str(refund.amount)})


def _sync_payment_refund_state(refund: Refund):
    if refund.payment is None or refund.status != RefundStatus.COMPLETED:
        return
    total = sum((r.amount for r in refund.payment.refunds.filter(status=RefundStatus.COMPLETED)), Decimal("0"))
    refund.payment.status = (PaymentStatus.REFUNDED if total >= refund.payment.amount
                             else PaymentStatus.PARTIALLY_REFUNDED)
    refund.payment.save(update_fields=["status"])


def initiate_refund(order_id, reason: str, amount: str | Decimal, actor_id=None, payment_id=None) -> Refund | None:
    """Create a refund record and call the provider. Safe to call from on_commit hooks.
    `payment_id` targets a specific captured payment (duplicate captures); otherwise the order's captured payment."""
    from apps.accounts.models import User

    order = Order.objects.get(pk=order_id)
    amount = q(amount)
    if amount <= 0:
        return None
    captured = (PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED)
    if payment_id:
        payment = order.payments.filter(pk=payment_id, status__in=captured).first()
    else:
        payment = order.payments.filter(status__in=captured).order_by("captured_at").first()
    if payment is None:
        return None
    already = sum((r.amount for r in payment.refunds.exclude(status=RefundStatus.FAILED)), Decimal("0"))
    if already + amount > payment.amount:
        amount = q(payment.amount - already)
        if amount <= 0:
            return None
    actor = User.objects.filter(pk=actor_id).first() if actor_id else None
    refund = Refund.objects.create(order=order, payment=payment, amount=amount, reason=reason[:255],
                                   initiated_by=actor, status=RefundStatus.PROCESSING)
    try:
        res = get_provider().refund(payment.provider_payment_id, amount, {"order": order.order_number,
                                                                          "reason": reason[:100]})
        refund.provider_refund_id = res["provider_refund_id"]
        refund.raw_response = res["raw"]
        if res["status"] == "processed":
            refund.status = RefundStatus.COMPLETED
            refund.processed_at = timezone.now()
        refund.save()
        _sync_payment_refund_state(refund)
    except Exception as e:  # noqa: BLE001
        log.exception("refund call failed for %s", order.order_number)
        refund.status = RefundStatus.PENDING  # admin retries from dashboard
        refund.failure_reason = str(e)[:255]
        refund.save(update_fields=["status", "failure_reason"])
    audit("payment.refund_initiated", target=refund, actor=actor, amount=str(amount), reason=reason)
    from apps.notifications.services import notify_order_event

    notify_order_event(order.id, "refund.initiated", {"amount": str(amount)})
    return refund


def mark_cod_collected(order: Order):
    from apps.riders.models import RiderProfile

    payment = order.payments.filter(method=PaymentMethod.COD).first()
    if payment and payment.status != PaymentStatus.COD_COLLECTED:
        payment.status = PaymentStatus.COD_COLLECTED
        payment.captured_at = timezone.now()
        payment.collected_by = order.rider
        payment.save(update_fields=["status", "captured_at", "collected_by"])
        Order.objects.filter(pk=order.pk).update(is_paid=True, paid_at=timezone.now())
        if order.rider_id:
            from django.db.models import F

            RiderProfile.objects.filter(pk=order.rider_id).update(cod_cash_in_hand=F("cod_cash_in_hand")
                                                                  + order.total_amount)


def is_online(method: str) -> bool:
    return method in ONLINE_METHODS
