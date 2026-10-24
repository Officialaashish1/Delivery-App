from decimal import Decimal

import pytest

from apps.coupons.models import Coupon, DiscountType
from apps.orders.pricing import delivery_fee_for_distance

from .conftest import fill_cart


@pytest.mark.django_db
class TestCartAndPricing:
    def test_delivery_fee_tiers(self):
        assert delivery_fee_for_distance(Decimal("1.5")) == Decimal("25.00")
        assert delivery_fee_for_distance(Decimal("4.5")) == Decimal("45.00")  # 25 + 2.5*8

    def test_cart_math_with_variant_and_addons(self, customer_api, menu, address):
        cart = fill_cart(customer_api, menu)
        # 2 x 190 + (399 + 40)
        assert cart["pricing"]["item_total"] == "819.00"
        assert cart["item_count"] == 3
        r = customer_api.patch("/api/v1/cart/", {"delivery_address_id": str(address.id)}, format="json")
        p = r.json()["pricing"]
        assert p["packaging_charge"] == "10.00" and p["platform_fee"] == "5.00"
        assert Decimal(p["tax_amount"]) == (Decimal("829.00") * Decimal("0.05")).quantize(Decimal("0.01"))
        assert Decimal(p["delivery_fee"]) >= Decimal("25.00")
        assert Decimal(p["total"]) == Decimal(p["item_total"]) + Decimal(p["packaging_charge"]) + Decimal(
            p["delivery_fee"]) + Decimal(p["platform_fee"]) + Decimal(p["tax_amount"])
        assert p["meets_min_order"] is True and p["errors"] == []

    def test_variant_required_and_addon_limits(self, customer_api, menu):
        r = customer_api.post("/api/v1/cart/", {"menu_item_id": str(menu["pizza"].id)}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "variant_required"
        r = customer_api.post("/api/v1/cart/", {"menu_item_id": str(menu["pizza"].id),
                                                "variant_id": str(menu["regular"].id),
                                                "addon_ids": [str(menu["cheese"].id), str(menu["olives"].id),
                                                              str(menu["jal"].id)]}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "addon_max"

    def test_single_restaurant_cart(self, customer_api, menu, partner):
        from django.contrib.gis.geos import Point

        from apps.menu.models import MenuCategory, MenuItem
        from apps.restaurants.models import Restaurant, RestaurantStatus

        other = Restaurant.objects.create(owner=partner, name="Other", owner_name="x", phone="+919000000002",
                                          address_line1="a", city="Surat", location=Point(72.87, 21.23, srid=4326),
                                          status=RestaurantStatus.APPROVED, is_open=True)
        cat = MenuCategory.objects.create(restaurant=other, name="c")
        item = MenuItem.objects.create(restaurant=other, category=cat, name="Burger", price=Decimal("99"))
        fill_cart(customer_api, menu)
        r = customer_api.post("/api/v1/cart/", {"menu_item_id": str(item.id)}, format="json")
        assert r.status_code == 409 and r.json()["error"]["code"] == "cart_restaurant_conflict"
        r = customer_api.post("/api/v1/cart/", {"menu_item_id": str(item.id), "replace_cart": True}, format="json")
        assert r.status_code == 201 and r.json()["item_count"] == 1 and r.json()["restaurant"]["name"] == "Other"

    def test_quantity_update_and_merge(self, customer_api, menu):
        customer_api.post("/api/v1/cart/", {"menu_item_id": str(menu["dal"].id), "quantity": 1}, format="json")
        r = customer_api.post("/api/v1/cart/", {"menu_item_id": str(menu["dal"].id), "quantity": 2}, format="json")
        assert len(r.json()["items"]) == 1 and r.json()["items"][0]["quantity"] == 3
        item_id = r.json()["items"][0]["id"]
        r = customer_api.patch(f"/api/v1/cart/items/{item_id}/", {"quantity": 0}, format="json")
        assert r.json()["item_count"] == 0 and r.json()["restaurant"] is None

    def test_coupons(self, customer_api, menu, address, restaurant):
        Coupon.objects.create(code="HALF", title="50%", discount_type=DiscountType.PERCENT, value=50,
                              max_discount=Decimal("100"), min_order_amount=Decimal("500"))
        Coupon.objects.create(code="FIRST", title="first", discount_type=DiscountType.FIXED, value=50,
                              first_order_only=True)
        Coupon.objects.create(code="FREEDEL", title="fd", discount_type=DiscountType.FREE_DELIVERY, value=0)
        fill_cart(customer_api, menu)
        customer_api.patch("/api/v1/cart/", {"delivery_address_id": str(address.id)}, format="json")
        r = customer_api.patch("/api/v1/cart/", {"coupon_code": "half"}, format="json")
        assert r.status_code == 200 and r.json()["pricing"]["coupon_discount"] == "100.00"
        r = customer_api.patch("/api/v1/cart/", {"coupon_code": "FREEDEL"}, format="json")
        p = r.json()["pricing"]
        assert p["delivery_discount"] == p["delivery_fee"] and p["coupon_discount"] == "0.00"
        r = customer_api.patch("/api/v1/cart/", {"coupon_code": "NOPE"}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "coupon_invalid"
        r = customer_api.get(f"/api/v1/coupons/?restaurant={restaurant.id}&subtotal=819")
        codes = {c["coupon"]["code"]: c["applicable"] for c in r.json()}
        assert codes == {"HALF": True, "FIRST": True, "FREEDEL": True}
        r = customer_api.get(f"/api/v1/coupons/?restaurant={restaurant.id}&subtotal=100")
        assert {c["coupon"]["code"]: c["applicable"] for c in r.json()}["HALF"] is False

    def test_discovery_and_search(self, customer_api, menu, restaurant):
        r = customer_api.get("/api/v1/restaurants/?lat=21.23&lng=72.87&sort=distance")
        assert r.status_code == 200 and r.json()["results"][0]["name"] == "Spice Villa"
        assert r.json()["results"][0]["distance_km"] is not None
        r = customer_api.get("/api/v1/restaurants/?lat=19.07&lng=72.87")  # Mumbai: out of range
        assert r.json()["count"] == 0
        r = customer_api.get(f"/api/v1/restaurants/{restaurant.slug}/?lat=21.23&lng=72.87")
        assert r.status_code == 200 and len(r.json()["menu"][0]["items"]) == 2
        r = customer_api.get("/api/v1/restaurants/search/?q=dal&lat=21.23&lng=72.87")
        assert r.json()["items"][0]["name"] == "Dal Makhani"
        r = customer_api.get("/api/v1/restaurants/home/?lat=21.23&lng=72.87")
        assert {s["key"] for s in r.json()["sections"]} >= {"nearby", "fast", "popular"}
        restaurant.is_open = False
        restaurant.save()
        r = customer_api.get("/api/v1/restaurants/?lat=21.23&lng=72.87&open_only=1")
        assert r.json()["count"] == 0
