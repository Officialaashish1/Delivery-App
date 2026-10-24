from datetime import timedelta
from decimal import Decimal

from django.db import transaction
from django.db.models import Avg, Count, Q, Sum
from django.utils import timezone

from apps.core.money import ZERO, q
from apps.orders.models import ACTIVE_STATUSES, Order, OrderStatus, PaymentMethod

from .models import PlatformLedger, RestaurantSettlement, SettlementLine, SettlementStatus


def record_restaurant_settlement_line(order: Order):
    if SettlementLine.objects.filter(order=order).exists():
        return
    gross = q(order.item_total + order.packaging_charge)
    coupon_ded = order.coupon_discount if (order.coupon and order.coupon.funded_by_restaurant) else ZERO
    SettlementLine.objects.create(
        restaurant_id=order.restaurant_id, order=order, gross=gross, commission_percent=order.commission_percent,
        commission=order.commission_amount, coupon_deduction=coupon_ded, refund_deduction=ZERO,
        tax_collected=order.tax_amount, net=q(gross - order.commission_amount - coupon_ded),
        is_cod=order.payment_method == PaymentMethod.COD, delivered_at=order.delivered_at or timezone.now())


def apply_refund_deduction(order: Order, amount: Decimal):
    """When a delivered order is refunded (quality complaint), deduct from the restaurant's unsettled line."""
    line = SettlementLine.objects.filter(order=order, settlement__isnull=True).first()
    if line:
        line.refund_deduction = q(line.refund_deduction + amount)
        line.net = q(line.gross - line.commission - line.coupon_deduction - line.refund_deduction)
        line.save(update_fields=["refund_deduction", "net"])


@transaction.atomic
def build_settlement(restaurant, period_start, period_end) -> RestaurantSettlement | None:
    lines = SettlementLine.objects.select_for_update().filter(
        restaurant=restaurant, settlement__isnull=True, delivered_at__date__gte=period_start,
        delivered_at__date__lte=period_end)
    if not lines.exists():
        return None
    # Reuse a still-pending settlement for the period; a paid/processing one is immutable, so late lines get a
    # supplementary settlement instead of being silently attached to a closed one.
    settlement = RestaurantSettlement.objects.select_for_update().filter(
        restaurant=restaurant, period_start=period_start, period_end=period_end,
        status=SettlementStatus.PENDING).first()
    if settlement is None:
        settlement = RestaurantSettlement.objects.create(restaurant=restaurant, period_start=period_start,
                                                         period_end=period_end)
    lines.update(settlement=settlement)
    _recompute_settlement(settlement)
    return settlement


def _recompute_settlement(settlement: RestaurantSettlement):
    agg = settlement.lines.aggregate(n=Count("id"), gross_sum=Sum("gross"), comm_sum=Sum("commission"),
                                     coupon_sum=Sum("coupon_deduction"), refund_sum=Sum("refund_deduction"),
                                     net_sum=Sum("net"), cod_sum=Sum("gross", filter=Q(is_cod=True)))
    settlement.order_count = agg["n"] or 0
    settlement.gross_sales = agg["gross_sum"] or ZERO
    settlement.commission_amount = agg["comm_sum"] or ZERO
    settlement.coupon_deductions = agg["coupon_sum"] or ZERO
    settlement.refund_deductions = agg["refund_sum"] or ZERO
    settlement.cod_collected_by_platform = agg["cod_sum"] or ZERO
    settlement.net_payable = q((agg["net_sum"] or ZERO) + settlement.adjustments)
    settlement.save()


def partner_dashboard(restaurant) -> dict:
    today = timezone.localdate()
    base = Order.objects.filter(restaurant=restaurant).exclude(status=OrderStatus.PENDING_PAYMENT)
    today_qs = base.filter(created_at__date=today)
    delivered_today = today_qs.filter(status=OrderStatus.DELIVERED)
    week_start = today - timedelta(days=today.weekday())
    month_start = today.replace(day=1)
    unsettled = SettlementLine.objects.filter(restaurant=restaurant, settlement__isnull=True).aggregate(
        s=Sum("net"))["s"] or ZERO
    last_settlement = RestaurantSettlement.objects.filter(restaurant=restaurant).first()

    def revenue(qs):
        return qs.filter(status=OrderStatus.DELIVERED).aggregate(s=Sum("restaurant_payout"))["s"] or ZERO

    return {
        "today": {
            "orders": today_qs.count(),
            "completed": delivered_today.count(),
            "pending": today_qs.filter(status__in=ACTIVE_STATUSES).count(),
            "new": today_qs.filter(status=OrderStatus.PLACED).count(),
            "cancelled": today_qs.filter(status__in=(OrderStatus.CANCELLED, OrderStatus.REJECTED)).count(),
            "revenue": str(revenue(today_qs)),
            "gross_sales": str(delivered_today.aggregate(s=Sum("item_total"))["s"] or ZERO),
            "avg_order_value": str(q(delivered_today.aggregate(a=Avg("item_total"))["a"] or ZERO)),
        },
        "week": {"orders": base.filter(created_at__date__gte=week_start, status=OrderStatus.DELIVERED).count(),
                 "revenue": str(revenue(base.filter(created_at__date__gte=week_start)))},
        "month": {"orders": base.filter(created_at__date__gte=month_start, status=OrderStatus.DELIVERED).count(),
                  "revenue": str(revenue(base.filter(created_at__date__gte=month_start)))},
        "rating_avg": str(restaurant.rating_avg), "rating_count": restaurant.rating_count,
        "total_orders": restaurant.total_orders,
        "is_open": restaurant.is_open, "is_accepting_orders": restaurant.is_accepting_orders,
        "is_open_now": restaurant.can_accept_orders_now(), "status": restaurant.status,
        "unsettled_amount": str(unsettled),
        "last_settlement": {"amount": str(last_settlement.net_payable), "status": last_settlement.status,
                            "period_end": last_settlement.period_end} if last_settlement else None,
        "commission_percent": str(restaurant.effective_commission_percent),
    }


def partner_finance_summary(restaurant, date_from=None, date_to=None) -> dict:
    lines = SettlementLine.objects.filter(restaurant=restaurant)
    if date_from:
        lines = lines.filter(delivered_at__date__gte=date_from)
    if date_to:
        lines = lines.filter(delivered_at__date__lte=date_to)
    agg = lines.aggregate(n=Count("id"), gross_sum=Sum("gross"), comm_sum=Sum("commission"),
                          coupon_sum=Sum("coupon_deduction"), refund_sum=Sum("refund_deduction"),
                          tax_sum=Sum("tax_collected"), net_sum=Sum("net"))
    return {k: (str(v) if isinstance(v, Decimal) else (v or 0)) for k, v in {
        "orders": agg["n"], "gross_sales": agg["gross_sum"] or ZERO, "commission": agg["comm_sum"] or ZERO,
        "coupon_deductions": agg["coupon_sum"] or ZERO, "refund_deductions": agg["refund_sum"] or ZERO,
        "tax_collected": agg["tax_sum"] or ZERO, "net_earnings": agg["net_sum"] or ZERO}.items()}


def rollup_platform_ledger(date):
    orders = Order.objects.filter(status=OrderStatus.DELIVERED, delivered_at__date=date)
    from apps.payments.models import Refund, RefundStatus

    agg = orders.aggregate(
        n=Count("id"), gov=Sum("total_amount"), comm=Sum("commission_amount"), dfee=Sum("delivery_fee"),
        pfee=Sum("platform_fee"), rider=Sum("rider_payout"),
        coupon=Sum("coupon_discount", filter=Q(coupon__funded_by_restaurant=False)))
    refunds = Refund.objects.filter(status=RefundStatus.COMPLETED, processed_at__date=date).aggregate(
        s=Sum("amount"))["s"] or ZERO
    comm, dfee, pfee = agg["comm"] or ZERO, agg["dfee"] or ZERO, agg["pfee"] or ZERO
    coupon, rider = agg["coupon"] or ZERO, agg["rider"] or ZERO
    PlatformLedger.objects.update_or_create(date=date, defaults={
        "orders": agg["n"] or 0, "gross_order_value": agg["gov"] or ZERO, "commission_revenue": comm,
        "delivery_fee_revenue": dfee, "platform_fee_revenue": pfee, "coupon_cost": coupon,
        "rider_payout_cost": rider, "refunds": refunds,
        "net_revenue": q(comm + dfee + pfee - coupon - rider - refunds)})


def mark_settlement_paid(settlement: RestaurantSettlement, actor, reference: str, note: str = ""):
    settlement.status = SettlementStatus.PAID
    settlement.reference = reference
    settlement.note = note
    settlement.processed_by = actor
    settlement.processed_at = timezone.now()
    settlement.save()
