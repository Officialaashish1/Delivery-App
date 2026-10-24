import logging
from datetime import timedelta

from celery import shared_task
from django.db import transaction
from django.utils import timezone

from apps.core.models import PlatformSettings

from .models import AssignmentStatus, Order, OrderStatus, RiderAssignment

log = logging.getLogger("localrush.orders.tasks")


@shared_task(name="apps.orders.tasks.dispatch_order", bind=True, max_retries=3)
def dispatch_order(self, order_id):
    from .services import offer_order_to_next_rider

    try:
        return str(getattr(offer_order_to_next_rider(order_id), "id", "")) or None
    except Exception as exc:  # noqa: BLE001
        log.exception("dispatch failed for %s", order_id)
        raise self.retry(exc=exc, countdown=5)


@shared_task(name="apps.orders.tasks.expire_stale_rider_offers")
def expire_stale_rider_offers():
    now = timezone.now()
    stale = RiderAssignment.objects.filter(status=AssignmentStatus.OFFERED, expires_at__lt=now)
    order_ids = list(stale.values_list("order_id", flat=True).distinct())
    stale.update(status=AssignmentStatus.EXPIRED, responded_at=now)
    for oid in order_ids:
        dispatch_order.delay(str(oid))
    return len(order_ids)


@shared_task(name="apps.orders.tasks.retry_unassigned_orders")
def retry_unassigned_orders():
    """Orders accepted/ready without a rider and without a live offer get re-dispatched periodically."""
    cutoff = timezone.now() - timedelta(seconds=20)
    qs = Order.objects.filter(
        status__in=(OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP),
        rider__isnull=True,
    ).filter(models_q_no_live_offer()).filter(
        models_q_attempt_old(cutoff))
    n = 0
    for o in qs[:200]:
        dispatch_order.delay(str(o.id))
        n += 1
    return n


def models_q_no_live_offer():
    from django.db.models import Exists, OuterRef, Q

    live = RiderAssignment.objects.filter(order=OuterRef("pk"), status=AssignmentStatus.OFFERED,
                                          expires_at__gt=timezone.now())
    return ~Q(Exists(live))


def models_q_attempt_old(cutoff):
    from django.db.models import Q

    return Q(last_assignment_attempt_at__isnull=True) | Q(last_assignment_attempt_at__lt=cutoff)


@shared_task(name="apps.orders.tasks.auto_reject_unaccepted_orders")
def auto_reject_unaccepted_orders():
    """Restaurant did not respond in time → cancel + refund so the customer is not left waiting."""
    from .services import cancel_order

    ps = PlatformSettings.load()
    cutoff = timezone.now() - timedelta(minutes=ps.restaurant_accept_timeout_minutes)
    qs = Order.objects.filter(status=OrderStatus.PLACED, placed_at__lt=cutoff)
    n = 0
    for order in qs[:100]:
        try:
            with transaction.atomic():
                cancel_order(order.id, None, "Restaurant did not accept the order in time", by="system")
            n += 1
        except Exception:  # noqa: BLE001
            log.exception("auto-reject failed for %s", order.order_number)
    return n


@shared_task(name="apps.orders.tasks.expire_pending_payments")
def expire_pending_payments():
    cutoff = timezone.now() - timedelta(minutes=20)
    from .services import transition

    for order in Order.objects.filter(status=OrderStatus.PENDING_PAYMENT, created_at__lt=cutoff)[:200]:
        with transaction.atomic():
            o = Order.objects.select_for_update().get(pk=order.pk)
            if o.status == OrderStatus.PENDING_PAYMENT:
                transition(o, OrderStatus.PAYMENT_FAILED, note="Payment not completed in time")
