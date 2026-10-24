"""
Single source of truth for order money math. Used by the cart preview and by order placement so the
customer always sees exactly what they will be charged. All values are Decimal.
"""
from dataclasses import dataclass, field
from decimal import Decimal

from apps.core.geo import haversine_km
from apps.core.models import PlatformSettings
from apps.core.money import ZERO, percent_of, q


def delivery_fee_for_distance(distance_km: Decimal, subtotal: Decimal = ZERO) -> Decimal:
    ps = PlatformSettings.load()
    if ps.free_delivery_above and subtotal >= ps.free_delivery_above:
        return ZERO
    extra_km = max(Decimal("0"), Decimal(str(distance_km)) - ps.base_delivery_km)
    fee = ps.base_delivery_fee + q(extra_km * ps.per_km_delivery_fee)
    return q(fee)


@dataclass
class PricedLine:
    menu_item_id: str
    name: str
    quantity: int
    unit_price: Decimal  # item or variant price
    addons_price: Decimal  # per unit
    packaging_charge: Decimal  # per unit
    food_type: str
    variant_name: str = ""
    addon_names: list = field(default_factory=list)
    special_instructions: str = ""
    image: str = ""

    @property
    def line_total(self) -> Decimal:
        return q((self.unit_price + self.addons_price) * self.quantity)

    @property
    def line_packaging(self) -> Decimal:
        return q(self.packaging_charge * self.quantity)


@dataclass
class PriceBreakdown:
    lines: list[PricedLine]
    item_total: Decimal = ZERO
    packaging_charge: Decimal = ZERO
    delivery_fee: Decimal = ZERO
    delivery_discount: Decimal = ZERO
    platform_fee: Decimal = ZERO
    tax_percent: Decimal = ZERO
    tax_amount: Decimal = ZERO
    coupon_code: str = ""
    coupon_discount: Decimal = ZERO
    distance_km: Decimal = ZERO
    total: Decimal = ZERO
    min_order_amount: Decimal = ZERO
    meets_min_order: bool = True
    errors: list = field(default_factory=list)

    def as_dict(self):
        return {
            "item_total": str(self.item_total),
            "packaging_charge": str(self.packaging_charge),
            "delivery_fee": str(self.delivery_fee),
            "delivery_discount": str(self.delivery_discount),
            "platform_fee": str(self.platform_fee),
            "tax_percent": str(self.tax_percent),
            "tax_amount": str(self.tax_amount),
            "coupon_code": self.coupon_code,
            "coupon_discount": str(self.coupon_discount),
            "distance_km": str(self.distance_km),
            "total": str(self.total),
            "min_order_amount": str(self.min_order_amount),
            "meets_min_order": self.meets_min_order,
            "errors": self.errors,
        }


def price_lines(lines: list[PricedLine], restaurant, delivery_point=None, coupon_code: str = "", user=None,
                raise_on_coupon_error=False) -> PriceBreakdown:
    ps = PlatformSettings.load()
    bd = PriceBreakdown(lines=lines)
    for ln in lines:
        if ln.line_total <= ZERO or ln.unit_price < ZERO or ln.addons_price < ZERO or ln.packaging_charge < ZERO:
            bd.errors.append(f"{ln.name} has an invalid price.")
    bd.item_total = q(sum((ln.line_total for ln in lines), ZERO))
    bd.packaging_charge = q(sum((ln.line_packaging for ln in lines), ZERO) + (
        restaurant.packaging_charge if lines else ZERO))
    bd.min_order_amount = restaurant.min_order_amount
    bd.meets_min_order = bd.item_total >= restaurant.min_order_amount
    if lines and not bd.meets_min_order:
        bd.errors.append(f"Minimum order amount is ₹{restaurant.min_order_amount:.0f}.")

    if delivery_point is not None:
        bd.distance_km = q(haversine_km(restaurant.latitude, restaurant.longitude, delivery_point.y,
                                        delivery_point.x) * 1.25)
        bd.delivery_fee = delivery_fee_for_distance(bd.distance_km, bd.item_total)
        if bd.distance_km > restaurant.delivery_radius_km or bd.distance_km > ps.max_delivery_radius_km:
            bd.errors.append("This restaurant does not deliver to the selected address.")
    bd.platform_fee = ps.platform_fee if lines else ZERO
    bd.tax_percent = ps.gst_percent
    bd.tax_amount = percent_of(bd.item_total + bd.packaging_charge, ps.gst_percent)

    if coupon_code and lines:
        from apps.core.exceptions import BusinessError
        from apps.coupons.services import apply_coupon

        try:
            result = apply_coupon(coupon_code, user, restaurant, bd.item_total, bd.delivery_fee)
            bd.coupon_code = result.coupon.code
            if result.free_delivery:
                bd.delivery_discount = bd.delivery_fee
            else:
                bd.coupon_discount = result.discount
        except BusinessError as e:
            if raise_on_coupon_error:
                raise
            bd.errors.append(str(e.detail))
    bd.total = q(bd.item_total + bd.packaging_charge + bd.delivery_fee - bd.delivery_discount + bd.platform_fee
                 + bd.tax_amount - bd.coupon_discount)
    if bd.total < ZERO:
        bd.total = ZERO
    return bd


def build_line(menu_item, quantity: int, variant=None, addons=(), special_instructions="") -> PricedLine:
    unit = variant.price if variant is not None else menu_item.effective_price
    addons_price = q(sum((a.price for a in addons), ZERO))
    return PricedLine(
        menu_item_id=str(menu_item.id), name=menu_item.name, quantity=quantity, unit_price=q(unit),
        addons_price=addons_price, packaging_charge=q(menu_item.packaging_charge), food_type=menu_item.food_type,
        variant_name=variant.name if variant is not None else "", addon_names=[a.name for a in addons],
        special_instructions=special_instructions, image=menu_item.image.name if menu_item.image else "",
    )
