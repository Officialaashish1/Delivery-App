import json
from decimal import Decimal

import pytest
from django.utils import timezone

from apps.coupons.models import Coupon, DiscountType
from apps.orders.models import Order, OrderStatus, RiderAssignment
from apps.payments.models import Payment, PaymentStatus, Refund
from apps.riders.models import PayoutStatus, RiderEarning, RiderPayout, RiderProfile

from .conftest import auth, fill_cart, place_cod_order
from .test_payments import fake_razorpay, sign, webhook_sig  # noqa: F401


def _assign_and_ready(api, oid, partner, rider):
    auth(api, partner).post(f"/api/v1/partner/orders/{oid}/accept/", {}, format="json")
    offer = RiderAssignment.objects.get(order_id=oid)
    auth(api, rider.user).post(f"/api/v1/riders/offers/{offer.id}/accept/")
    auth(api, partner).post(f"/api/v1/partner/orders/{oid}/ready/")


@pytest.mark.django_db(transaction=True)
class TestSecurityFixes:
    def test_otp_lockout_after_five_wrong_attempts(self, api, customer, address, partner, restaurant, menu, rider):
        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        _assign_and_ready(api, oid, partner, rider)
        rd = auth(api, rider.user)
        o = Order.objects.get(pk=oid)
        wrong = "0000" if o.pickup_otp != "0000" else "0001"
        for i in range(5):
            r = rd.post(f"/api/v1/riders/orders/{oid}/pickup/", {"otp": wrong}, format="json")
            assert r.status_code == 400 and r.json()["error"]["attempts_left"] == 4 - i
        r = rd.post(f"/api/v1/riders/orders/{oid}/pickup/", {"otp": o.pickup_otp}, format="json")  # even the right code
        assert r.status_code == 423 and r.json()["error"]["code"] == "otp_locked"
        assert Order.objects.get(pk=oid).status == OrderStatus.RIDER_ASSIGNED

    def test_coupon_cannot_be_stacked_across_pending_orders(self, api, customer, address, restaurant, menu,
                                                            fake_razorpay):
        Coupon.objects.create(code="ONCE", title="once", discount_type=DiscountType.FIXED, value=50,
                              usage_limit_per_user=1)
        c = auth(api, customer)
        fill_cart(c, menu)
        c.patch("/api/v1/cart/", {"delivery_address_id": str(address.id), "coupon_code": "ONCE"}, format="json")
        r = c.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "upi"}, format="json")
        assert r.status_code == 201 and r.json()["order"]["status"] == "pending_payment"
        fill_cart(c, menu)
        r = c.patch("/api/v1/cart/", {"coupon_code": "ONCE"}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "coupon_usage_exhausted"

    def test_first_order_coupon_blocked_by_any_live_order(self, api, customer, address, restaurant, menu):
        Coupon.objects.create(code="FIRST", title="f", discount_type=DiscountType.FIXED, value=50,
                              first_order_only=True)
        place_cod_order(auth(api, customer), menu, address)
        c = auth(api, customer)
        fill_cart(c, menu)
        r = c.patch("/api/v1/cart/", {"coupon_code": "FIRST"}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "coupon_first_order_only"

    def test_no_oversell_beyond_stock(self, api, customer, address, restaurant, menu):
        c = auth(api, customer)
        r = c.post("/api/v1/cart/", {"menu_item_id": str(menu["pizza"].id), "quantity": 6,
                                     "variant_id": str(menu["regular"].id)}, format="json")  # stock is 5
        assert r.status_code == 201
        c.patch("/api/v1/cart/", {"delivery_address_id": str(address.id)}, format="json")
        r = c.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "cod"}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "cart_item_unavailable"
        menu["pizza"].refresh_from_db()
        assert menu["pizza"].stock_quantity == 5

    def test_negative_prices_rejected(self, api, partner, restaurant, menu):
        p = auth(api, partner)
        r = p.patch(f"/api/v1/partner/menu/items/{menu['dal'].id}/", {"price": "-10.00"}, format="json")
        assert r.status_code == 400
        r = p.patch(f"/api/v1/partner/menu/items/{menu['dal'].id}/", {
            "addon_groups": [{"name": "x", "options": [{"name": "free money", "price": "-500.00"}]}]}, format="json")
        assert r.status_code == 400

    def test_duplicate_capture_is_refunded_not_kept(self, api, customer, address, restaurant, menu, fake_razorpay):
        c = auth(api, customer)
        fill_cart(c, menu)
        r = c.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "upi"}, format="json")
        oid = r.json()["order"]["id"]
        amount = int(Decimal(r.json()["order"]["total_amount"]) * 100)
        anon = api.__class__()

        def capture(pid, event_id):
            body = json.dumps({"event": "payment.captured", "payload": {"payment": {"entity": {
                "id": pid, "order_id": "order_test123", "amount": amount, "status": "captured"}}}}).encode()
            return anon.post("/api/v1/payments/webhooks/razorpay/", body, content_type="application/json",
                             HTTP_X_RAZORPAY_SIGNATURE=webhook_sig("whsec_test", body), HTTP_X_RAZORPAY_EVENT_ID=event_id)

        # customer retries → second provider order/payment; first capture lands late after the second succeeded
        c.post(f"/api/v1/payments/orders/{oid}/retry/")
        first_payment = Payment.objects.filter(order_id=oid, status=PaymentStatus.FAILED).first()  # superseded
        assert first_payment is not None
        assert capture("pay_second", "evt_a").status_code == 200
        assert Order.objects.get(pk=oid).status == OrderStatus.PLACED
        # make the superseded attempt capturable again (Razorpay order remained payable) and replay a late capture
        Payment.objects.filter(pk=first_payment.pk).update(status=PaymentStatus.CREATED)
        Payment.objects.filter(order_id=oid).exclude(pk=first_payment.pk).update(provider_order_id="order_other")
        assert capture("pay_first_late", "evt_b").status_code == 200
        late = Payment.objects.get(provider_payment_id="pay_first_late")
        refund = Refund.objects.get(payment=late)
        assert refund.amount == late.amount and late.status == PaymentStatus.REFUNDED
        assert Order.objects.get(pk=oid).status == OrderStatus.PLACED  # order unaffected
        # a capture for an order cancelled while Checkout was open is refunded too
        fill_cart(c, menu)
        r = c.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "upi"}, format="json")
        oid2 = r.json()["order"]["id"]
        Payment.objects.filter(order_id=oid2).update(provider_order_id="order_cancelled")
        c.post(f"/api/v1/orders/{oid2}/cancel/", {"reason": "oops"}, format="json")
        body = json.dumps({"event": "payment.captured", "payload": {"payment": {"entity": {
            "id": "pay_after_cancel", "order_id": "order_cancelled", "amount": amount, "status": "captured"}}}}).encode()
        anon.post("/api/v1/payments/webhooks/razorpay/", body, content_type="application/json",
                  HTTP_X_RAZORPAY_SIGNATURE=webhook_sig("whsec_test", body), HTTP_X_RAZORPAY_EVENT_ID="evt_c")
        assert Refund.objects.filter(order_id=oid2).exists()
        assert Order.objects.get(pk=oid2).status == OrderStatus.CANCELLED

    def test_payout_cannot_be_processed_twice(self, api, admin, rider):
        RiderEarning.objects.create(rider=rider, earning_type="delivery", amount=Decimal("100"))
        RiderProfile.objects.filter(pk=rider.pk).update(wallet_balance=Decimal("100"), upi_id="a@upi")
        payout = RiderPayout.objects.create(rider=rider, amount=Decimal("100"))
        a = auth(api, admin)
        assert a.post(f"/api/v1/admin/rider-payouts/{payout.id}/process/", {"status": "paid"}, format="json").status_code == 200
        r = a.post(f"/api/v1/admin/rider-payouts/{payout.id}/process/", {"status": "paid"}, format="json")
        assert r.status_code == 409
        rider.refresh_from_db()
        assert rider.wallet_balance == 0

    def test_partner_tracking_snapshot_hides_customer_phone(self, api, customer, address, partner, restaurant, menu):
        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        snap = auth(api, partner).get(f"/api/v1/tracking/orders/{oid}/").json()
        assert "delivery_phone" not in snap["order"] and "delivery_otp" not in snap["order"]
        snap = auth(api, customer).get(f"/api/v1/tracking/orders/{oid}/").json()
        assert snap["order"]["delivery_address_text"]

    def test_admin_cannot_toggle_status_via_patch(self, api, admin, customer):
        r = auth(api, admin).patch(f"/api/v1/admin/users/{customer.id}/", {"is_suspended": True}, format="json")
        assert r.status_code == 200
        customer.refresh_from_db()
        assert customer.is_suspended is False

    def test_cancel_twice_does_not_restock_twice(self, api, customer, address, partner, restaurant, menu, admin):
        oid = place_cod_order(auth(api, customer), menu, address)["id"]
        auth(api, customer).post(f"/api/v1/orders/{oid}/cancel/", {"reason": "x"}, format="json")
        r = auth(api, admin).post(f"/api/v1/admin/orders/{oid}/cancel/", {"reason": "again", "force": True}, format="json")
        assert r.status_code == 409
        menu["pizza"].refresh_from_db()
        assert menu["pizza"].stock_quantity == 5

    def test_late_settlement_lines_get_supplementary_settlement(self, api, customer, address, partner, restaurant, menu,
                                                                 rider, admin):
        from apps.finance.models import RestaurantSettlement
        from apps.finance.services import build_settlement

        import uuid

        def deliver_one():
            oid = place_cod_order(auth(api, customer), menu, address, key=uuid.uuid4().hex)["id"]
            _assign_and_ready(api, oid, partner, rider)
            o = Order.objects.get(pk=oid)
            rd = auth(api, rider.user)
            rd.post(f"/api/v1/riders/orders/{oid}/pickup/", {"otp": o.pickup_otp}, format="json")
            r = rd.post(f"/api/v1/riders/orders/{oid}/deliver/", {"otp": o.delivery_otp})
            assert r.status_code == 200, r.content
            return Order.objects.get(pk=oid)

        o1 = deliver_one()
        today = timezone.localdate()
        s1 = build_settlement(restaurant, today, today)
        assert s1.net_payable == o1.restaurant_payout
        s1.status = "paid"
        s1.save()
        o2 = deliver_one()
        s2 = build_settlement(restaurant, today, today)
        assert s2.pk != s1.pk and s2.net_payable == o2.restaurant_payout
        assert RestaurantSettlement.objects.filter(restaurant=restaurant).count() == 2
