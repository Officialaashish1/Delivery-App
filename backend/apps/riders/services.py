from datetime import timedelta
from decimal import Decimal

from django.db import transaction
from django.db.models import F, Sum
from django.utils import timezone

from apps.core.exceptions import BusinessError, ConflictError
from apps.core.models import PlatformSettings
from apps.core.money import ZERO, q
from apps.orders.models import RIDER_ACTIVE_STATUSES, Order, OrderStatus

from .models import EarningType, IncentiveRule, PayoutStatus, RiderEarning, RiderPayout, RiderProfile


def credit_delivery_earning(order: Order):
    """Idempotent: one DELIVERY earning per order."""
    if order.rider_id is None or RiderEarning.objects.filter(order=order, earning_type=EarningType.DELIVERY).exists():
        return
    ps = PlatformSettings.load()
    base = ps.rider_base_pay
    distance_pay = q(ps.rider_per_km_pay * max(ZERO, order.distance_km - Decimal("1")))
    RiderEarning.objects.create(rider_id=order.rider_id, order=order, earning_type=EarningType.DELIVERY, amount=base,
                                description=f"Delivery {order.order_number}")
    if distance_pay > 0:
        RiderEarning.objects.create(rider_id=order.rider_id, order=order, earning_type=EarningType.DISTANCE,
                                    amount=distance_pay, distance_km=order.distance_km,
                                    description=f"{order.distance_km} km")
    RiderProfile.objects.filter(pk=order.rider_id).update(wallet_balance=F("wallet_balance") + base + distance_pay)
    _check_incentives(order.rider_id)


def _check_incentives(rider_id):
    rider = RiderProfile.objects.get(pk=rider_id)
    today = timezone.localdate()
    delivered_today = Order.objects.filter(rider_id=rider_id, status=OrderStatus.DELIVERED,
                                           delivered_at__date=today).count()
    rules = IncentiveRule.objects.filter(is_active=True).filter(
        models_q_city(rider.city)).filter(models_q_dates(today))
    for rule in rules:
        if delivered_today >= rule.deliveries_required and not RiderEarning.objects.filter(
                rider_id=rider_id, earning_type=EarningType.INCENTIVE, created_at__date=today,
                description=f"incentive:{rule.id}").exists():
            RiderEarning.objects.create(rider_id=rider_id, earning_type=EarningType.INCENTIVE,
                                        amount=rule.bonus_amount, description=f"incentive:{rule.id}")
            RiderProfile.objects.filter(pk=rider_id).update(wallet_balance=F("wallet_balance") + rule.bonus_amount)


def models_q_city(city):
    from django.db.models import Q

    return Q(city="") | Q(city__iexact=city)


def models_q_dates(today):
    from django.db.models import Q

    return (Q(starts_on__isnull=True) | Q(starts_on__lte=today)) & (Q(ends_on__isnull=True) | Q(ends_on__gte=today))


def earnings_summary(rider: RiderProfile) -> dict:
    now = timezone.localtime()
    today = now.date()
    week_start = today - timedelta(days=today.weekday())
    month_start = today.replace(day=1)

    def total(since):
        return RiderEarning.objects.filter(rider=rider, created_at__date__gte=since).exclude(
            earning_type=EarningType.COD_DEPOSIT).aggregate(s=Sum("amount"))["s"] or ZERO

    def count(since):
        return Order.objects.filter(rider=rider, status=OrderStatus.DELIVERED, delivered_at__date__gte=since).count()

    return {
        "today_earnings": str(total(today)), "today_deliveries": count(today),
        "week_earnings": str(total(week_start)), "week_deliveries": count(week_start),
        "month_earnings": str(total(month_start)), "month_deliveries": count(month_start),
        "wallet_balance": str(rider.wallet_balance), "cod_cash_in_hand": str(rider.cod_cash_in_hand),
        "lifetime_deliveries": rider.total_deliveries, "rating_avg": str(rider.rating_avg),
        "pending_payout": str(RiderPayout.objects.filter(rider=rider, status__in=(
            PayoutStatus.REQUESTED, PayoutStatus.PROCESSING)).aggregate(s=Sum("amount"))["s"] or ZERO),
        "incentives": [{"name": r.name, "deliveries_required": r.deliveries_required,
                        "bonus_amount": str(r.bonus_amount),
                        "progress": count(today)} for r in IncentiveRule.objects.filter(is_active=True).filter(
            models_q_city(rider.city)).filter(models_q_dates(today))],
    }


@transaction.atomic
def request_payout(rider: RiderProfile, amount: Decimal | None = None) -> RiderPayout:
    rider = RiderProfile.objects.select_for_update().get(pk=rider.pk)
    available = q(rider.wallet_balance - rider.cod_cash_in_hand)
    if RiderPayout.objects.filter(rider=rider, status__in=(PayoutStatus.REQUESTED, PayoutStatus.PROCESSING)).exists():
        raise ConflictError("A payout request is already pending.", code="payout_pending")
    amount = q(amount) if amount is not None else available
    if amount <= 0 or amount > available:
        raise BusinessError(f"Available for withdrawal: ₹{max(available, ZERO)}.", code="insufficient_balance")
    if not (rider.bank_account_number or rider.upi_id):
        raise BusinessError("Add bank account or UPI details first.", code="bank_details_missing")
    payout = RiderPayout.objects.create(rider=rider, amount=amount)
    return payout


@transaction.atomic
def set_online(rider: RiderProfile, online: bool) -> RiderProfile:
    rider = RiderProfile.objects.select_for_update().get(pk=rider.pk)
    if online and not rider.is_approved:
        raise BusinessError("Your account is not approved yet.", code="rider_not_approved")
    if not online and Order.objects.filter(rider=rider, status__in=RIDER_ACTIVE_STATUSES).exists():
        raise ConflictError("Finish your current delivery before going offline.", code="delivery_in_progress")
    rider.is_online = online
    rider.last_online_at = timezone.now()
    if not online:
        rider.is_on_delivery = False
    rider.save(update_fields=["is_online", "last_online_at", "is_on_delivery"])
    return rider
