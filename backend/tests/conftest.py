from datetime import time
from decimal import Decimal

import pytest
from django.contrib.gis.geos import Point
from rest_framework.test import APIClient

from apps.accounts.authentication import issue_tokens
from apps.accounts.models import Address, CustomerProfile, User, UserRole
from apps.core.models import PlatformSettings
from apps.menu.models import (
    AddonGroup,
    FoodType,
    MenuAddon,
    MenuCategory,
    MenuItem,
    MenuVariant,
    VariantGroup,
)
from apps.restaurants.models import Restaurant, RestaurantHours, RestaurantStatus
from apps.riders.models import RiderProfile, RiderStatus

LAT, LNG = 21.2300, 72.8700


@pytest.fixture(autouse=True)
def _settings_row(db):
    from django.core.cache import cache

    cache.clear()
    from apps.tracking import store as tracking_store

    tracking_store._memory.__init__()  # reset the in-memory redis shim between tests
    ps = PlatformSettings.load()
    ps.platform_fee = Decimal("5.00")
    ps.gst_percent = Decimal("5.00")
    ps.base_delivery_fee = Decimal("25.00")
    ps.base_delivery_km = Decimal("2.00")
    ps.per_km_delivery_fee = Decimal("8.00")
    ps.rider_base_pay = Decimal("20.00")
    ps.rider_per_km_pay = Decimal("6.00")
    ps.save()
    return ps


@pytest.fixture
def api():
    return APIClient()


def auth(client: APIClient, user: User):
    client.credentials(HTTP_AUTHORIZATION="Bearer " + issue_tokens(user)["access"])
    return client


@pytest.fixture
def customer(db):
    u = User.objects.create_user(phone="+919200000001", role=UserRole.CUSTOMER, full_name="Priya", is_phone_verified=True)
    CustomerProfile.objects.create(user=u)
    return u


@pytest.fixture
def address(customer):
    return Address.objects.create(user=customer, address_line1="12 Lajamni Chowk", city="Surat",
                                  location=Point(LNG + 0.01, LAT + 0.008, srid=4326), is_default=True)


@pytest.fixture
def partner(db):
    u = User.objects.create_user(phone="+919000000001", password="partner12345", role=UserRole.PARTNER,
                                 full_name="Rakesh", is_phone_verified=True)
    return u


@pytest.fixture
def restaurant(partner):
    r = Restaurant.objects.create(owner=partner, name="Spice Villa", owner_name="Rakesh", phone="+919000000001",
                                  address_line1="Main road", city="Surat", location=Point(LNG, LAT, srid=4326),
                                  status=RestaurantStatus.APPROVED, is_open=True, packaging_charge=Decimal("10"),
                                  min_order_amount=Decimal("100"), avg_preparation_minutes=20)
    RestaurantHours.objects.bulk_create([RestaurantHours(restaurant=r, weekday=d, open_time=time(0, 0),
                                                         close_time=time(23, 59)) for d in range(7)])
    return r


@pytest.fixture
def menu(restaurant):
    cat = MenuCategory.objects.create(restaurant=restaurant, name="Mains")
    dal = MenuItem.objects.create(restaurant=restaurant, category=cat, name="Dal Makhani", price=Decimal("190"),
                                  food_type=FoodType.VEG)
    pizza = MenuItem.objects.create(restaurant=restaurant, category=cat, name="Margherita", price=Decimal("199"),
                                    discounted_price=Decimal("179"), stock_quantity=5)
    vg = VariantGroup.objects.create(item=pizza, name="Size")
    regular = MenuVariant.objects.create(group=vg, name="Regular", price=Decimal("199"), is_default=True)
    large = MenuVariant.objects.create(group=vg, name="Large", price=Decimal("399"))
    ag = AddonGroup.objects.create(item=pizza, name="Toppings", max_select=2)
    cheese = MenuAddon.objects.create(group=ag, name="Extra cheese", price=Decimal("40"))
    olives = MenuAddon.objects.create(group=ag, name="Olives", price=Decimal("30"))
    jal = MenuAddon.objects.create(group=ag, name="Jalapenos", price=Decimal("30"))
    return {"cat": cat, "dal": dal, "pizza": pizza, "regular": regular, "large": large, "cheese": cheese,
            "olives": olives, "jal": jal}


@pytest.fixture
def rider(db):
    u = User.objects.create_user(phone="+919100000001", role=UserRole.RIDER, full_name="Amit", is_phone_verified=True)
    rp = RiderProfile.objects.create(user=u, full_name="Amit", city="Surat", vehicle_number="GJ05AB1000",
                                     status=RiderStatus.APPROVED, is_online=True,
                                     current_location=Point(LNG + 0.002, LAT - 0.002, srid=4326))
    from django.utils import timezone

    rp.location_updated_at = timezone.now()
    rp.save()
    return rp


@pytest.fixture
def admin(db):
    return User.objects.create_superuser(phone="+919999999999", password="admin12345", full_name="Admin")


@pytest.fixture
def customer_api(api, customer):
    return auth(api, customer)


def fill_cart(client, menu):
    r = client.post("/api/v1/cart/", {"menu_item_id": str(menu["dal"].id), "quantity": 2}, format="json")
    assert r.status_code == 201, r.content
    r = client.post("/api/v1/cart/", {"menu_item_id": str(menu["pizza"].id), "quantity": 1,
                                      "variant_id": str(menu["large"].id),
                                      "addon_ids": [str(menu["cheese"].id)]}, format="json")
    assert r.status_code == 201, r.content
    return r.json()


def place_cod_order(client, menu, address, key="k1"):
    fill_cart(client, menu)
    r = client.patch("/api/v1/cart/", {"delivery_address_id": str(address.id)}, format="json")
    assert r.status_code == 200, r.content
    r = client.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "cod",
                                        "idempotency_key": key}, format="json")
    assert r.status_code == 201, r.content
    return r.json()["order"]
