from django.db import transaction

from apps.core.exceptions import BusinessError
from apps.menu.models import MenuAddon, MenuItem, MenuVariant
from apps.orders.pricing import build_line, price_lines

from .models import Cart, CartItem


def get_cart(user) -> Cart:
    cart, _ = Cart.objects.select_related("restaurant", "delivery_address").get_or_create(user=user)
    return cart


def validate_customisation(item: MenuItem, variant_id, addon_ids):
    variant = None
    groups = list(item.variant_groups.prefetch_related("options"))
    if groups:
        if variant_id is None:
            if any(g.is_required for g in groups):
                raise BusinessError("Please choose a variant.", code="variant_required")
        else:
            variant = MenuVariant.objects.filter(id=variant_id, group__item=item, is_available=True).first()
            if variant is None:
                raise BusinessError("Selected variant is unavailable.", code="variant_unavailable")
    elif variant_id is not None:
        raise BusinessError("This item has no variants.", code="variant_invalid")
    addons = []
    if addon_ids:
        addons = list(MenuAddon.objects.filter(id__in=addon_ids, group__item=item, is_available=True)
                      .select_related("group"))
        if len(addons) != len(set(addon_ids)):
            raise BusinessError("One or more add-ons are unavailable.", code="addon_unavailable")
    counts: dict = {}
    for a in addons:
        counts[a.group_id] = counts.get(a.group_id, 0) + 1
    for g in item.addon_groups.all():
        n = counts.get(g.id, 0)
        if n < g.min_select:
            raise BusinessError(f"Choose at least {g.min_select} from {g.name}.", code="addon_min")
        if n > g.max_select:
            raise BusinessError(f"Choose at most {g.max_select} from {g.name}.", code="addon_max")
    return variant, addons


@transaction.atomic
def add_to_cart(user, menu_item_id, quantity=1, variant_id=None, addon_ids=None, special_instructions="",
                replace_cart=False) -> Cart:
    addon_ids = list(addon_ids or [])
    item = MenuItem.objects.select_related("restaurant").filter(id=menu_item_id).first()
    if item is None:
        raise BusinessError("Item not found.", code="not_found", status_code=404)
    if not item.in_stock:
        raise BusinessError("This item is currently unavailable.", code="item_unavailable")
    if not item.restaurant.is_approved:
        raise BusinessError("This restaurant is not accepting orders.", code="restaurant_closed")
    cart = Cart.objects.select_for_update().get_or_create(user=user)[0]
    if cart.restaurant_id and cart.restaurant_id != item.restaurant_id and cart.items.exists():
        if not replace_cart:
            raise BusinessError("Your cart has items from another restaurant.", code="cart_restaurant_conflict",
                                status_code=409, extra={"current_restaurant": cart.restaurant.name})
        cart.items.all().delete()
        cart.coupon_code = ""
    cart.restaurant = item.restaurant
    cart.save()
    variant, addons = validate_customisation(item, variant_id, addon_ids)
    if quantity < 1:
        raise BusinessError("Quantity must be at least 1.", code="validation_error")
    if quantity > 20:
        raise BusinessError("Maximum 20 of one item per order.", code="validation_error")
    for existing in cart.items.filter(menu_item=item):
        if existing.same_config_as(variant, set(addon_ids)) and existing.special_instructions == special_instructions:
            existing.quantity = min(20, existing.quantity + quantity)
            existing.save(update_fields=["quantity"])
            return cart
    ci = CartItem.objects.create(cart=cart, menu_item=item, variant=variant, quantity=quantity,
                                 special_instructions=special_instructions)
    ci.addons.set(addons)
    return cart


@transaction.atomic
def update_cart_item(user, cart_item_id, quantity: int) -> Cart:
    cart = get_cart(user)
    ci = cart.items.filter(id=cart_item_id).first()
    if ci is None:
        raise BusinessError("Cart item not found.", code="not_found", status_code=404)
    if quantity <= 0:
        ci.delete()
    else:
        ci.quantity = min(20, quantity)
        ci.save(update_fields=["quantity"])
    if not cart.items.exists():
        cart.clear()
    return cart


def priced_lines_for_cart(cart: Cart):
    lines, problems = [], []
    for ci in cart.items.select_related("menu_item", "variant").prefetch_related("addons"):
        mi = ci.menu_item
        if mi.deleted_at is not None or not mi.in_stock:
            problems.append(f"{mi.name} is no longer available.")
            continue
        if ci.variant and not ci.variant.is_available:
            problems.append(f"{mi.name} ({ci.variant.name}) is no longer available.")
            continue
        lines.append(build_line(mi, ci.quantity, ci.variant, list(ci.addons.all()), ci.special_instructions))
    return lines, problems


def price_cart(cart: Cart, user, address=None, coupon_code=None):
    lines, problems = priced_lines_for_cart(cart)
    if cart.restaurant is None:
        from apps.orders.pricing import PriceBreakdown

        return PriceBreakdown(lines=[]), problems
    address = address or cart.delivery_address
    point = address.location if address else None
    bd = price_lines(lines, cart.restaurant, point, coupon_code if coupon_code is not None else cart.coupon_code,
                     user)
    bd.errors = problems + bd.errors
    return bd, problems
