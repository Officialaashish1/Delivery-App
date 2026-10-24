from datetime import timedelta

from django.db.models import Avg, Count, Q, Sum
from django.db.models.functions import TruncDate
from django.utils import timezone

from apps.accounts.models import User, UserRole
from apps.core.money import ZERO
from apps.finance.models import RestaurantSettlement, SettlementStatus
from apps.orders.models import ACTIVE_STATUSES, Order, OrderStatus
from apps.restaurants.models import Restaurant, RestaurantStatus
from apps.riders.models import PayoutStatus, RiderPayout, RiderProfile, RiderStatus


def overview() -> dict:
    today = timezone.localdate()
    orders_today = Order.objects.filter(created_at__date=today).exclude(status=OrderStatus.PENDING_PAYMENT)
    delivered_today = orders_today.filter(status=OrderStatus.DELIVERED)
    month_start = today.replace(day=1)
    delivered_month = Order.objects.filter(status=OrderStatus.DELIVERED, delivered_at__date__gte=month_start)
    agg = delivered_today.aggregate(gov=Sum("total_amount"), comm=Sum("commission_amount"), dfee=Sum("delivery_fee"),
                                    pfee=Sum("platform_fee"), rider=Sum("rider_payout"))
    return {
        "users": {
            "total": User.objects.filter(role=UserRole.CUSTOMER).count(),
            "active_30d": User.objects.filter(role=UserRole.CUSTOMER,
                                              orders__created_at__gte=timezone.now() - timedelta(days=30)).distinct().count(),
            "new_today": User.objects.filter(role=UserRole.CUSTOMER, created_at__date=today).count(),
        },
        "restaurants": {
            "total": Restaurant.objects.count(),
            "approved": Restaurant.objects.filter(status=RestaurantStatus.APPROVED).count(),
            "pending": Restaurant.objects.filter(status__in=(RestaurantStatus.PENDING, RestaurantStatus.UNDER_REVIEW)).count(),
            "open_now": Restaurant.objects.filter(status=RestaurantStatus.APPROVED, is_open=True).count(),
        },
        "riders": {
            "total": RiderProfile.objects.count(),
            "approved": RiderProfile.objects.filter(status=RiderStatus.APPROVED).count(),
            "pending": RiderProfile.objects.filter(status__in=(RiderStatus.PENDING, RiderStatus.UNDER_REVIEW)).count(),
            "online": RiderProfile.objects.filter(is_online=True).count(),
            "on_delivery": RiderProfile.objects.filter(is_on_delivery=True).count(),
        },
        "orders_today": {
            "total": orders_today.count(),
            "active": Order.objects.filter(status__in=ACTIVE_STATUSES).count(),
            "completed": delivered_today.count(),
            "cancelled": orders_today.filter(status__in=(OrderStatus.CANCELLED, OrderStatus.REJECTED,
                                                         OrderStatus.DELIVERY_FAILED)).count(),
            "awaiting_restaurant": Order.objects.filter(status=OrderStatus.PLACED).count(),
            "awaiting_rider": Order.objects.filter(status__in=(OrderStatus.READY_FOR_PICKUP,), rider__isnull=True).count(),
            "gross_order_value": str(agg["gov"] or ZERO),
            "platform_revenue": str((agg["comm"] or ZERO) + (agg["dfee"] or ZERO) + (agg["pfee"] or ZERO)
                                    - (agg["rider"] or ZERO)),
            "commission": str(agg["comm"] or ZERO),
            "avg_order_value": str(round(delivered_today.aggregate(a=Avg("total_amount"))["a"] or 0, 2)),
        },
        "month": {
            "orders": delivered_month.count(),
            "gross_order_value": str(delivered_month.aggregate(s=Sum("total_amount"))["s"] or ZERO),
            "commission": str(delivered_month.aggregate(s=Sum("commission_amount"))["s"] or ZERO),
        },
        "pending_settlements": str(RestaurantSettlement.objects.filter(status=SettlementStatus.PENDING).aggregate(
            s=Sum("net_payable"))["s"] or ZERO),
        "pending_rider_payouts": str(RiderPayout.objects.filter(status=PayoutStatus.REQUESTED).aggregate(
            s=Sum("amount"))["s"] or ZERO),
        "open_tickets": _open_tickets(),
    }


def _open_tickets():
    from apps.support.models import SupportTicket

    return SupportTicket.objects.filter(status__in=("open", "in_progress")).count()


def sales_series(days=30) -> list[dict]:
    start = timezone.localdate() - timedelta(days=days - 1)
    rows = (Order.objects.filter(created_at__date__gte=start).exclude(status=OrderStatus.PENDING_PAYMENT)
            .annotate(d=TruncDate("created_at")).values("d")
            .annotate(orders=Count("id"),
                      delivered=Count("id", filter=Q(status=OrderStatus.DELIVERED)),
                      cancelled=Count("id", filter=Q(status__in=(OrderStatus.CANCELLED, OrderStatus.REJECTED))),
                      sales=Sum("total_amount", filter=Q(status=OrderStatus.DELIVERED)),
                      commission=Sum("commission_amount", filter=Q(status=OrderStatus.DELIVERED)),
                      new_customers=Count("customer", distinct=True))
            .order_by("d"))
    by_day = {r["d"]: r for r in rows}
    out = []
    for i in range(days):
        d = start + timedelta(days=i)
        r = by_day.get(d, {})
        out.append({"date": d.isoformat(), "orders": r.get("orders", 0), "delivered": r.get("delivered", 0),
                    "cancelled": r.get("cancelled", 0), "sales": str(r.get("sales") or ZERO),
                    "commission": str(r.get("commission") or ZERO), "customers": r.get("new_customers", 0)})
    return out


def restaurant_performance(days=30, limit=20):
    since = timezone.now() - timedelta(days=days)
    return list(Restaurant.objects.filter(orders__created_at__gte=since).annotate(
        orders=Count("orders", filter=Q(orders__created_at__gte=since)),
        delivered=Count("orders", filter=Q(orders__created_at__gte=since, orders__status=OrderStatus.DELIVERED)),
        rejected=Count("orders", filter=Q(orders__created_at__gte=since, orders__status=OrderStatus.REJECTED)),
        sales=Sum("orders__total_amount", filter=Q(orders__created_at__gte=since, orders__status=OrderStatus.DELIVERED)),
        commission=Sum("orders__commission_amount", filter=Q(orders__created_at__gte=since,
                                                            orders__status=OrderStatus.DELIVERED)),
    ).order_by("-sales").values("id", "name", "city", "rating_avg", "orders", "delivered", "rejected", "sales",
                                "commission")[:limit])


def rider_performance(days=30, limit=20):
    since = timezone.now() - timedelta(days=days)
    return list(RiderProfile.objects.filter(orders__created_at__gte=since).annotate(
        deliveries=Count("orders", filter=Q(orders__created_at__gte=since, orders__status=OrderStatus.DELIVERED)),
        failed=Count("orders", filter=Q(orders__created_at__gte=since, orders__status=OrderStatus.DELIVERY_FAILED)),
        earnings=Sum("orders__rider_payout", filter=Q(orders__created_at__gte=since,
                                                     orders__status=OrderStatus.DELIVERED)),
        avg_km=Avg("orders__distance_km", filter=Q(orders__created_at__gte=since)),
    ).order_by("-deliveries").values("id", "full_name", "city", "rating_avg", "deliveries", "failed", "earnings",
                                     "avg_km", "total_rejections")[:limit])


def cancellation_report(days=30):
    since = timezone.now() - timedelta(days=days)
    qs = Order.objects.filter(created_at__gte=since, status__in=(OrderStatus.CANCELLED, OrderStatus.REJECTED,
                                                                 OrderStatus.DELIVERY_FAILED))
    return {
        "total": qs.count(),
        "by_actor": list(qs.values("cancelled_by").annotate(n=Count("id")).order_by("-n")),
        "by_status": list(qs.values("status").annotate(n=Count("id")).order_by("-n")),
        "top_reasons": list(qs.exclude(cancellation_reason="").values("cancellation_reason").annotate(
            n=Count("id")).order_by("-n")[:10]),
    }


def retention_report():
    from apps.accounts.models import CustomerProfile

    total = CustomerProfile.objects.count()
    repeat = CustomerProfile.objects.filter(total_orders__gte=2).count()
    one = CustomerProfile.objects.filter(total_orders=1).count()
    never = CustomerProfile.objects.filter(total_orders=0).count()
    return {"total_customers": total, "repeat_customers": repeat, "single_order_customers": one,
            "never_ordered": never, "repeat_rate": round(repeat / total * 100, 1) if total else 0}
