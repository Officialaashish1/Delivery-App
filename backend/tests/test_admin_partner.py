from decimal import Decimal

import pytest

from apps.orders.models import Order, RiderAssignment
from apps.restaurants.models import Restaurant, RestaurantStatus
from apps.riders.models import RiderProfile

from .conftest import auth, place_cod_order


@pytest.mark.django_db(transaction=True)
class TestPartnerOnboardingAndMenu:
    def test_register_restaurant_then_menu(self, api, partner):
        p = auth(api, partner)
        assert p.get("/api/v1/partner/restaurant/").status_code == 404
        r = p.post("/api/v1/partner/restaurant/", {"name": "New Cafe", "owner_name": "R", "phone": "9000000001",
                                                   "address_line1": "x", "city": "Surat", "latitude": 21.2,
                                                   "longitude": 72.8, "fssai_number": "123"}, format="json")
        assert r.status_code == 201 and r.json()["status"] == "pending" and len(r.json()["hours"]) == 7
        assert p.post("/api/v1/partner/restaurant/", {"name": "Dup"}, format="json").status_code == 409
        r = p.post("/api/v1/partner/restaurant/toggle/", {"is_open": True}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "not_approved"
        r = p.post("/api/v1/partner/menu/categories/", {"name": "Starters"}, format="json")
        cat = r.json()["id"]
        r = p.post("/api/v1/partner/menu/items/", {
            "category": cat, "name": "Paneer Tikka", "price": "220.00", "food_type": "veg",
            "variant_groups": [{"name": "Portion", "options": [{"name": "Half", "price": "140.00"},
                                                               {"name": "Full", "price": "220.00"}]}],
            "addon_groups": [{"name": "Extras", "min_select": 0, "max_select": 2,
                              "options": [{"name": "Extra chutney", "price": "10.00"}]}]}, format="json")
        assert r.status_code == 201, r.content
        item = r.json()
        assert len(item["variant_groups"][0]["options"]) == 2 and item["addon_groups"][0]["options"][0]["name"]
        r = p.post(f"/api/v1/partner/menu/items/{item['id']}/availability/", {"is_available": False}, format="json")
        assert r.json()["is_available"] is False
        r = p.patch(f"/api/v1/partner/menu/items/{item['id']}/", {"discounted_price": "300.00"}, format="json")
        assert r.status_code == 400
        assert p.delete(f"/api/v1/partner/menu/categories/{cat}/").status_code == 400  # not empty
        r = p.put("/api/v1/partner/restaurant/hours/", {"hours": [{"weekday": 0, "open_time": "10:00",
                                                                     "close_time": "22:00"}]}, format="json")
        assert r.status_code == 200 and len(r.json()) == 1

    def test_admin_approval_flow(self, api, partner, admin):
        p = auth(api, partner)
        rid = p.post("/api/v1/partner/restaurant/", {"name": "New Cafe", "owner_name": "R", "phone": "9000000001",
                                                     "address_line1": "x", "city": "Surat", "latitude": 21.2,
                                                     "longitude": 72.8}, format="json").json()["id"]
        a = auth(api, admin)
        assert a.get("/api/v1/admin/restaurants/?status=pending").json()["count"] == 1
        r = a.post(f"/api/v1/admin/restaurants/{rid}/decide/", {"approve": False, "reason": "FSSAI missing"},
                   format="json")
        assert r.json()["status"] == "rejected"
        # owner edits → back to pending
        auth(api, partner).patch("/api/v1/partner/restaurant/", {"fssai_number": "12345678901234"}, format="json")
        assert Restaurant.objects.get(pk=rid).status == RestaurantStatus.PENDING
        r = auth(api, admin).post(f"/api/v1/admin/restaurants/{rid}/decide/", {"approve": True}, format="json")
        assert r.json()["status"] == "approved"
        r = auth(api, partner).post("/api/v1/partner/restaurant/toggle/", {"is_open": True}, format="json")
        assert r.json()["is_open"] is True
        # notification reached the owner
        assert any("approved" in n["title"].lower() for n in
                   auth(api, partner).get("/api/v1/notifications/").json()["results"])


@pytest.mark.django_db(transaction=True)
class TestAdminOps:
    def test_rider_approval_gate(self, api, admin):
        from apps.accounts.models import User

        u = User.objects.create_user(phone="+919100000050", role="rider")
        rd = auth(api, u)
        r = rd.patch("/api/v1/riders/profile/", {"full_name": "New Rider", "vehicle_number": "GJ05XX0001",
                                                 "city": "Surat"}, format="json")
        assert r.status_code == 200 and r.json()["status"] == "pending"
        r = rd.post("/api/v1/riders/online/", {"is_online": True}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "rider_not_approved"
        rp = RiderProfile.objects.get(user=u)
        a = auth(api, admin)
        r = a.post(f"/api/v1/admin/riders/{rp.id}/decide/", {"approve": True}, format="json")
        assert r.json()["status"] == "approved"
        r = auth(api, u).post("/api/v1/riders/online/", {"is_online": True, "latitude": 21.2, "longitude": 72.8},
                              format="json")
        assert r.json()["is_online"] is True

    def test_admin_cancel_reassign_overview(self, api, customer, address, partner, restaurant, menu, rider, admin):
        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        auth(api, partner).post(f"/api/v1/partner/orders/{oid}/accept/", {}, format="json")
        a = auth(api, admin)
        r = a.get("/api/v1/admin/overview/")
        assert r.json()["orders_today"]["total"] == 1 and r.json()["orders_today"]["active"] == 1
        r = a.get(f"/api/v1/admin/orders/{oid}/candidate-riders/")
        assert r.json()[0]["full_name"] == "Amit"
        r = a.post(f"/api/v1/admin/orders/{oid}/assign-rider/", {"rider_id": str(rider.id)}, format="json")
        assert r.status_code == 200 and r.json()["rider_name"] == "Amit"
        assert RiderAssignment.objects.filter(order_id=oid, status="accepted", is_manual=True).exists()
        r = a.post(f"/api/v1/admin/orders/{oid}/cancel/", {"reason": "fraud"}, format="json")
        assert r.json()["status"] == "cancelled"
        rider.refresh_from_db()
        assert rider.is_on_delivery is False
        r = a.get("/api/v1/admin/reports/cancellations/")
        assert r.json()["total"] == 1 and r.json()["by_actor"][0]["cancelled_by"] == "admin"
        r = a.get("/api/v1/admin/audit-logs/")
        assert any(x["action"] == "order.cancelled" for x in r.json())

    def test_suspend_user_revokes_access(self, api, customer, admin):
        a = auth(api, admin)
        r = a.post(f"/api/v1/admin/users/{customer.id}/suspend/", {"suspend": True, "reason": "abuse"}, format="json")
        assert r.json()["is_suspended"] is True
        assert auth(api, customer).get("/api/v1/auth/me/").status_code == 401
        r = api.post("/api/v1/auth/otp/request/", {"phone": customer.phone, "role": "customer"}, format="json")
        assert r.status_code == 403

    def test_settings_update_affects_pricing(self, api, admin, customer, menu, address):
        a = auth(api, admin)
        r = a.patch("/api/v1/admin/settings/", {"platform_fee": "9.00", "gst_percent": "0"}, format="json")
        assert r.status_code == 200
        from .conftest import fill_cart

        c = auth(api, customer)
        fill_cart(c, menu)
        p = c.patch("/api/v1/cart/", {"delivery_address_id": str(address.id)}, format="json").json()["pricing"]
        assert p["platform_fee"] == "9.00" and p["tax_amount"] == "0.00"

    def test_settlement_generation_and_payout(self, api, customer, address, partner, restaurant, menu, rider, admin):
        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        auth(api, partner).post(f"/api/v1/partner/orders/{oid}/accept/", {}, format="json")
        offer = RiderAssignment.objects.get(order_id=oid)
        auth(api, rider.user).post(f"/api/v1/riders/offers/{offer.id}/accept/")
        auth(api, partner).post(f"/api/v1/partner/orders/{oid}/ready/")
        o = Order.objects.get(pk=oid)
        auth(api, rider.user).post(f"/api/v1/riders/orders/{oid}/pickup/", {"otp": o.pickup_otp}, format="json")
        auth(api, rider.user).post(f"/api/v1/riders/orders/{oid}/deliver/", {"otp": o.delivery_otp})
        a = auth(api, admin)
        r = a.post("/api/v1/admin/settlements/generate/", {}, format="json")
        assert r.json()["generated"] == 1
        st = a.get("/api/v1/admin/settlements/").json()["results"][0]
        assert Decimal(st["net_payable"]) == o.restaurant_payout
        r = a.post(f"/api/v1/admin/settlements/{st['id']}/mark-paid/", {"reference": "UTR123"}, format="json")
        assert r.json()["status"] == "paid"
        p = auth(api, partner)
        assert p.get("/api/v1/partner/finance/settlements/").json()["results"][0]["status"] == "paid"
        assert p.get("/api/v1/partner/finance/statement.csv").status_code == 200
        # rider payout: wallet has earnings but COD cash in hand exceeds it → insufficient
        rd = auth(api, rider.user)
        r = rd.post("/api/v1/riders/payouts/request/", {}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] in ("insufficient_balance", "bank_details_missing")
        RiderProfile.objects.filter(pk=rider.pk).update(cod_cash_in_hand=0, upi_id="amit@upi")
        r = auth(api, rider.user).post("/api/v1/riders/payouts/request/", {}, format="json")
        assert r.status_code == 201 and Decimal(r.json()["amount"]) == o.rider_payout
        pid = r.json()["id"]
        r = auth(api, admin).post(f"/api/v1/admin/rider-payouts/{pid}/process/", {"status": "paid", "reference": "x"},
                                  format="json")
        assert r.json()["status"] == "paid"
        rider.refresh_from_db()
        assert rider.wallet_balance == 0

    def test_support_ticket_roundtrip(self, api, customer, address, partner, restaurant, menu, admin):
        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        c = auth(api, customer)
        r = c.post("/api/v1/support/tickets/", {"order": oid, "category": "missing_item", "subject": "No naan",
                                                "description": "Naan missing"}, format="json")
        assert r.status_code == 201
        tid = r.json()["id"]
        a = auth(api, admin)
        r = a.post(f"/api/v1/admin/tickets/{tid}/reply/", {"text": "Sorry! Refunding ₹45", "status": "resolved"},
                   format="json")
        assert r.status_code == 200
        t = auth(api, customer).get(f"/api/v1/support/tickets/{tid}/").json()
        assert t["status"] == "resolved" and len(t["messages"]) == 2 and t["messages"][1]["is_staff_reply"]
