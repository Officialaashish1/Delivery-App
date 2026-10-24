from dataclasses import dataclass
from decimal import Decimal

from django.db.models import Q
from django.utils import timezone

from apps.core.exceptions import BusinessError
from apps.core.money import ZERO, percent_of, q

from .models import Coupon, DiscountType


@dataclass
class CouponResult:
    coupon: Coupon
    discount: Decimal
    free_delivery: bool


def _live_coupons_for(restaurant):
    now = timezone.now()
    return Coupon.objects.filter(is_active=True, starts_at__lte=now).filter(
        Q(expires_at__isnull=True) | Q(expires_at__gte=now)).filter(
        Q(restaurant__isnull=True) | Q(restaurant=restaurant))


def best_offer_text(restaurant) -> str:
    """Cached for 60 s per restaurant: shown on every restaurant card."""
    from django.core.cache import cache

    key = f"offer_text:{restaurant.id}"
    cached = cache.get(key)
    if cached is not None:
        return cached
    c = _live_coupons_for(restaurant).filter(is_public=True).order_by("-value").first()
    text = c.short_text() if c else ""
    cache.set(key, text, 60)
    return text


def available_coupons(user, restaurant, subtotal: Decimal):
    results = []
    for c in _live_coupons_for(restaurant).filter(is_public=True):
        try:
            r = apply_coupon(c, user, restaurant, subtotal, raise_errors=True)
            results.append({"coupon": c, "applicable": True, "discount": r.discount, "reason": ""})
        except BusinessError as e:
            results.append({"coupon": c, "applicable": False, "discount": ZERO, "reason": str(e.detail)})
    return results


def apply_coupon(coupon_or_code, user, restaurant, subtotal: Decimal, delivery_fee: Decimal = ZERO,
                 raise_errors=True) -> CouponResult:
    if isinstance(coupon_or_code, str):
        coupon = Coupon.objects.filter(code=coupon_or_code.strip().upper()).first()
        if coupon is None:
            raise BusinessError("Invalid coupon code.", code="coupon_invalid")
    else:
        coupon = coupon_or_code
    if not coupon.is_live:
        raise BusinessError("This coupon has expired.", code="coupon_expired")
    if coupon.restaurant_id and coupon.restaurant_id != restaurant.id:
        raise BusinessError("This coupon is not valid for this restaurant.", code="coupon_restaurant_mismatch")
    if subtotal < coupon.min_order_amount:
        raise BusinessError(f"Add items worth ₹{coupon.min_order_amount - subtotal:.0f} more to use this coupon.",
                            code="coupon_min_order", extra={"min_order_amount": str(coupon.min_order_amount)})
    from apps.orders.models import Order, OrderStatus

    dead = (OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.PAYMENT_FAILED)
    if coupon.first_order_only and Order.objects.filter(customer=user).exclude(status__in=dead).exists():
        raise BusinessError("This coupon is valid only on your first order.", code="coupon_first_order_only")
    # Count every live order carrying the coupon (not just confirmed usages) so pending orders cannot stack it.
    used_by_user = Order.objects.filter(coupon=coupon, customer=user).exclude(status__in=dead).count()
    if used_by_user >= coupon.usage_limit_per_user:
        raise BusinessError("You have already used this coupon.", code="coupon_usage_exhausted")
    if coupon.usage_limit_total is not None:
        live_total = Order.objects.filter(coupon=coupon).exclude(status__in=dead).count()
        if live_total >= coupon.usage_limit_total:
            raise BusinessError("This coupon has been fully redeemed.", code="coupon_expired")
    if coupon.discount_type == DiscountType.PERCENT:
        discount = percent_of(subtotal, coupon.value)
        if coupon.max_discount:
            discount = min(discount, coupon.max_discount)
        return CouponResult(coupon, q(discount), False)
    if coupon.discount_type == DiscountType.FIXED:
        return CouponResult(coupon, q(min(coupon.value, subtotal)), False)
    return CouponResult(coupon, q(delivery_fee), True)
