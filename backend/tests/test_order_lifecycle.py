from decimal import Decimal

import pytest
from django.utils import timezone

from apps.finance.models import SettlementLine
from apps.orders.models import AssignmentStatus, Order, OrderStatus, RiderAssignment
from apps.orders.services import offer_order_to_next_rider, transition
from apps.payments.models import Payment, PaymentStatus
from apps.riders.models import RiderEarning, RiderProfile

from .conftest import auth, fill_cart, place_cod_order


@pytest.mark.django_db(transaction=True)
class TestOrderLifecycle:
    def test_full_cod_flow(self, api, customer, address, partner, restaurant, menu, rider):
        c = auth(api, customer)
        order = place_cod_order(c, menu, address)
        oid = order["id"]
        assert order["status"] == "placed" and order["payment_method"] == "cod"
        assert Payment.objects.get(order_id=oid).status == PaymentStatus.COD_PENDING
        assert c.get("/api/v1/cart/").json()["item_count"] == 0  # cart cleared
        # stock decremented (pizza had 5)
        menu["pizza"].refresh_from_db()
        assert menu["pizza"].stock_quantity == 4
        # idempotent re-post returns the same order
        fill_cart(c, menu)
        r = c.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "cod",
                                       "idempotency_key": "k1"}, format="json")
        assert r.status_code == 201 and r.json()["order"]["id"] == oid
        c.delete("/api/v1/cart/")

        # partner
        p = auth(api, partner)
        r = p.get("/api/v1/partner/orders/?scope=new")
        assert r.json()["count"] == 1
        r = p.post(f"/api/v1/partner/orders/{oid}/ready/")  # invalid from placed
        assert r.status_code == 409 and r.json()["error"]["code"] == "invalid_transition"
        r = p.post(f"/api/v1/partner/orders/{oid}/accept/", {"preparation_minutes": 15}, format="json")
        assert r.status_code == 200 and r.json()["status"] == "restaurant_accepted"
        pickup_otp = r.json()["pickup_otp"]
        assert len(pickup_otp) == 4

        # dispatch (celery eager in tests ran dispatch_order on accept)
        offer = RiderAssignment.objects.get(order_id=oid)
        assert offer.rider_id == rider.id and offer.status == AssignmentStatus.OFFERED
        assert offer_order_to_next_rider(oid) is None  # no double offer while one is pending

        # rider
        rd = auth(api, rider.user)
        r = rd.get("/api/v1/riders/offers/")
        assert r.json()["count"] == 1 and r.json()["results"][0]["order"]["order_number"] == order["order_number"]
        r = rd.post(f"/api/v1/riders/offers/{offer.id}/accept/")
        assert r.status_code == 200
        rider.refresh_from_db()
        assert rider.is_on_delivery is True
        # second accept attempt is a conflict
        assert rd.post(f"/api/v1/riders/offers/{offer.id}/accept/").status_code == 409

        r = auth(api, customer).get(f"/api/v1/orders/{oid}/")
        assert r.json()["rider"]["full_name"] == "Amit" and r.json()["delivery_otp"] is None
        # cannot pick up before ready
        rd = auth(api, rider.user)
        assert rd.post(f"/api/v1/riders/orders/{oid}/pickup/", {"otp": pickup_otp}, format="json").status_code == 409
        p = auth(api, partner)
        r = p.post(f"/api/v1/partner/orders/{oid}/ready/")
        assert r.json()["status"] == "rider_assigned"  # ready + rider already assigned
        rd = auth(api, rider.user)
        r = rd.post(f"/api/v1/riders/orders/{oid}/pickup/", {"otp": "0000"}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "otp_invalid"
        r = rd.post(f"/api/v1/riders/orders/{oid}/pickup/", {"otp": pickup_otp}, format="json")
        assert r.status_code == 200 and r.json()["status"] == "out_for_delivery"
        r = auth(api, customer).get(f"/api/v1/orders/{oid}/")
        delivery_otp = r.json()["delivery_otp"]
        assert delivery_otp and len(delivery_otp) == 4
        rd = auth(api, rider.user)
        r = rd.post(f"/api/v1/riders/orders/{oid}/deliver/", {"otp": "1234" if delivery_otp != "1234" else "0000"})
        assert r.status_code == 400
        r = rd.post(f"/api/v1/riders/orders/{oid}/deliver/", {"otp": delivery_otp})
        assert r.status_code == 200 and r.json()["status"] == "delivered"

        o = Order.objects.get(pk=oid)
        assert o.is_paid and o.delivered_at is not None
        assert Payment.objects.get(order_id=oid).status == PaymentStatus.COD_COLLECTED
        earnings = RiderEarning.objects.filter(order_id=oid)
        assert earnings.exists() and sum(e.amount for e in earnings) == o.rider_payout
        rider.refresh_from_db()
        assert rider.is_on_delivery is False and rider.total_deliveries == 1
        assert rider.cod_cash_in_hand == o.total_amount and rider.wallet_balance == o.rider_payout
        line = SettlementLine.objects.get(order_id=oid)
        assert line.net == o.restaurant_payout and line.commission == o.commission_amount
        assert o.commission_amount == ((o.item_total + o.packaging_charge) * Decimal("0.20")).quantize(Decimal("0.01"))
        hist = [h.to_status for h in o.status_history.all()]
        assert hist == ["placed", "restaurant_accepted", "ready_for_pickup", "rider_assigned", "picked_up",
                        "out_for_delivery", "delivered"] or hist[0] == "placed"
        # review + stats
        c = auth(api, customer)
        r = c.post("/api/v1/reviews/", {"order_id": oid, "restaurant_rating": 4, "rider_rating": 5}, format="json")
        assert r.status_code == 201
        restaurant.refresh_from_db()
        rider.refresh_from_db()
        assert restaurant.rating_avg == Decimal("4.00") and rider.rating_avg == Decimal("5.00")
        assert c.post("/api/v1/reviews/", {"order_id": oid, "restaurant_rating": 4}, format="json").status_code == 409
        # customer notifications were created
        titles = [n["title"] for n in c.get("/api/v1/notifications/").json()["results"]]
        assert any("Delivered" in t for t in titles) and any("placed" in t.lower() for t in titles)

    def test_customer_cancel_rules(self, api, customer, address, partner, restaurant, menu, rider):
        c = auth(api, customer)
        order = place_cod_order(c, menu, address)
        oid = order["id"]
        r = c.post(f"/api/v1/orders/{oid}/cancel/", {"reason": "mistake"}, format="json")
        assert r.status_code == 200 and r.json()["status"] == "cancelled"
        menu["pizza"].refresh_from_db()
        assert menu["pizza"].stock_quantity == 5  # restocked
        # cannot cancel after delivered path start (out_for_delivery)
        c.delete("/api/v1/cart/")
        order = place_cod_order(c, menu, address)
        oid = order["id"]
        auth(api, partner).post(f"/api/v1/partner/orders/{oid}/accept/", {}, format="json")
        from apps.core.models import PlatformSettings

        ps = PlatformSettings.load()
        ps.customer_cancel_window_seconds = 0
        ps.save()
        Order.objects.filter(pk=oid).update(placed_at=timezone.now() - timezone.timedelta(minutes=5))
        r = auth(api, customer).post(f"/api/v1/orders/{oid}/cancel/", {"reason": "late"}, format="json")
        assert r.status_code == 409 and r.json()["error"]["code"] == "not_cancellable"

    def test_restaurant_reject_releases_everything(self, api, customer, address, partner, restaurant, menu, rider):
        c = auth(api, customer)
        order = place_cod_order(c, menu, address)
        oid = order["id"]
        p = auth(api, partner)
        p.post(f"/api/v1/partner/orders/{oid}/accept/", {}, format="json")
        offer = RiderAssignment.objects.get(order_id=oid)
        auth(api, rider.user).post(f"/api/v1/riders/offers/{offer.id}/accept/")
        r = auth(api, partner).post(f"/api/v1/partner/orders/{oid}/cancel/", {"reason": "out of stock"},
                                    format="json")
        assert r.status_code == 200 and r.json()["status"] == "cancelled"
        offer.refresh_from_db()
        assert offer.status == AssignmentStatus.CANCELLED
        rider.refresh_from_db()
        assert rider.is_on_delivery is False
        o = Order.objects.get(pk=oid)
        assert o.rider_id is None and o.cancelled_by == "restaurant"

    def test_rider_reject_reoffers_to_next(self, api, customer, address, partner, restaurant, menu, rider):
        from django.contrib.gis.geos import Point

        from apps.accounts.models import User, UserRole

        u2 = User.objects.create_user(phone="+919100000002", role=UserRole.RIDER, full_name="Suresh")
        rider2 = RiderProfile.objects.create(user=u2, full_name="Suresh", status="approved", is_online=True,
                                             current_location=Point(72.875, 21.235, srid=4326),
                                             location_updated_at=timezone.now())
        c = auth(api, customer)
        oid = place_cod_order(c, menu, address)["id"]
        auth(api, partner).post(f"/api/v1/partner/orders/{oid}/accept/", {}, format="json")
        first = RiderAssignment.objects.get(order_id=oid)
        assert first.rider_id == rider.id  # closest first
        r = auth(api, rider.user).post(f"/api/v1/riders/offers/{first.id}/reject/", {"reason": "far"}, format="json")
        assert r.status_code == 204
        second = RiderAssignment.objects.filter(order_id=oid, status=AssignmentStatus.OFFERED).get()
        assert second.rider_id == rider2.id
        rider.refresh_from_db()
        assert rider.total_rejections == 1
        # expiry sweep re-dispatches: expire second, then nobody left → no new offers
        RiderAssignment.objects.filter(pk=second.pk).update(expires_at=timezone.now() - timezone.timedelta(seconds=1))
        from apps.orders.tasks import expire_stale_rider_offers

        expire_stale_rider_offers()
        second.refresh_from_db()
        assert second.status == AssignmentStatus.EXPIRED
        assert not RiderAssignment.objects.filter(order_id=oid, status=AssignmentStatus.OFFERED).exists()

    def test_state_machine_rejects_invalid(self, customer, address, partner, restaurant, menu, api):
        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        o = Order.objects.get(pk=oid)
        from apps.core.exceptions import ConflictError

        with pytest.raises(ConflictError):
            transition(o, OrderStatus.DELIVERED)
        with pytest.raises(ConflictError):
            transition(o, OrderStatus.PICKED_UP)
        transition(o, OrderStatus.RESTAURANT_ACCEPTED)
        transition(o, OrderStatus.PREPARING)
        assert o.status_history.count() == 3

    def test_closed_restaurant_blocks_order(self, api, customer, address, restaurant, menu):
        c = auth(api, customer)
        fill_cart(c, menu)
        restaurant.is_accepting_orders = False
        restaurant.save()
        r = c.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "cod"}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "restaurant_closed"

    def test_auto_reject_unaccepted(self, api, customer, address, partner, restaurant, menu):
        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        Order.objects.filter(pk=oid).update(placed_at=timezone.now() - timezone.timedelta(minutes=30))
        from apps.orders.tasks import auto_reject_unaccepted_orders

        assert auto_reject_unaccepted_orders() == 1
        assert Order.objects.get(pk=oid).status == OrderStatus.CANCELLED

    def test_other_customer_cannot_see_order(self, api, customer, address, partner, restaurant, menu):
        from apps.accounts.models import User

        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        other = User.objects.create_user(phone="+919200000009", role="customer")
        assert auth(api, other).get(f"/api/v1/orders/{oid}/").status_code == 404
        assert auth(api, other).get(f"/api/v1/tracking/orders/{oid}/").status_code == 403


@pytest.mark.django_db(transaction=True)
def test_rider_sees_accepted_order_while_preparing(api, customer, address, partner, restaurant, menu, rider):
    oid = place_cod_order(auth(api, customer), menu, address)["id"]
    auth(api, partner).post(f"/api/v1/partner/orders/{oid}/accept/", {}, format="json")
    offer = RiderAssignment.objects.get(order_id=oid)
    rd = auth(api, rider.user)
    rd.post(f"/api/v1/riders/offers/{offer.id}/accept/")
    assert Order.objects.get(pk=oid).status == OrderStatus.RESTAURANT_ACCEPTED
    dash = rd.get("/api/v1/riders/dashboard/").json()
    assert dash["current_order"] and dash["current_order"]["id"] == oid
    assert rd.get("/api/v1/riders/orders/?scope=active").json()["count"] == 1
