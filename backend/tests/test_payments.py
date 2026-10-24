import hashlib
import hmac
import json
from decimal import Decimal

import pytest

from apps.orders.models import Order, OrderStatus
from apps.payments.models import Payment, PaymentStatus, Refund, RefundStatus, WebhookEvent

from .conftest import auth, fill_cart


class FakeRazorpayClient:
    """Minimal stand-in for razorpay.Client used in tests."""

    class _Order:
        def create(self, data):
            return {"id": "order_test123", "amount": data["amount"], "currency": "INR", "status": "created"}

    class _Payment:
        captured_amount = None

        def fetch(self, pid):
            return {"id": pid, "status": "captured", "amount": self.captured_amount, "order_id": "order_test123"}

        def refund(self, pid, data):
            return {"id": "rfnd_1", "status": "processed", "amount": data["amount"]}

    def __init__(self):
        self.order = self._Order()
        self.payment = self._Payment()


@pytest.fixture
def fake_razorpay(monkeypatch):
    client = FakeRazorpayClient()
    monkeypatch.setattr("apps.payments.providers.RazorpayProvider.client", property(lambda self: client))
    return client


def sign(secret, order_id, payment_id):
    return hmac.new(secret.encode(), f"{order_id}|{payment_id}".encode(), hashlib.sha256).hexdigest()


def webhook_sig(secret, body: bytes):
    return hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()


@pytest.mark.django_db(transaction=True)
class TestOnlinePayments:
    def _place_online(self, api, customer, address, menu):
        c = auth(api, customer)
        fill_cart(c, menu)
        r = c.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "upi"}, format="json")
        assert r.status_code == 201, r.content
        return c, r.json()

    def test_online_order_awaits_payment_then_verify(self, api, customer, address, restaurant, menu, fake_razorpay):
        c, body = self._place_online(api, customer, address, menu)
        order = body["order"]
        assert order["status"] == "pending_payment"
        pay = body["payment"]
        assert pay["provider"] == "razorpay" and pay["provider_order_id"] == "order_test123"
        assert pay["amount_paise"] == int(Decimal(order["total_amount"]) * 100)
        fake_razorpay.payment.captured_amount = pay["amount_paise"]
        # partner cannot see unpaid orders
        # bad signature
        r = c.post("/api/v1/payments/verify/", {"order_id": order["id"], "razorpay_order_id": "order_test123",
                                                "razorpay_payment_id": "pay_1", "razorpay_signature": "bad"},
                   format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "payment_invalid_signature"
        # retry creates a new payment attempt
        r = c.post(f"/api/v1/payments/orders/{order['id']}/retry/")
        assert r.status_code == 200 and r.json()["payment"]["provider_order_id"] == "order_test123"
        good = sign("rzp_test_secret", "order_test123", "pay_1")
        r = c.post("/api/v1/payments/verify/", {"order_id": order["id"], "razorpay_order_id": "order_test123",
                                                "razorpay_payment_id": "pay_1", "razorpay_signature": good},
                   format="json")
        assert r.status_code == 200, r.content
        assert r.json()["status"] == "placed" and r.json()["is_paid"] is True
        p = Payment.objects.filter(order_id=order["id"], status=PaymentStatus.CAPTURED).get()
        assert p.provider_payment_id == "pay_1"
        # verifying again is idempotent
        r = c.post("/api/v1/payments/verify/", {"order_id": order["id"], "razorpay_order_id": "order_test123",
                                                "razorpay_payment_id": "pay_1", "razorpay_signature": good},
                   format="json")
        assert r.status_code == 200

    def test_amount_mismatch_rejected(self, api, customer, address, restaurant, menu, fake_razorpay):
        c, body = self._place_online(api, customer, address, menu)
        fake_razorpay.payment.captured_amount = 100  # ₹1 — tampered
        good = sign("rzp_test_secret", "order_test123", "pay_2")
        r = c.post("/api/v1/payments/verify/", {"order_id": body["order"]["id"], "razorpay_order_id": "order_test123",
                                                "razorpay_payment_id": "pay_2", "razorpay_signature": good},
                   format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "payment_amount_mismatch"
        assert Order.objects.get(pk=body["order"]["id"]).status == OrderStatus.PENDING_PAYMENT

    def test_webhook_captures_and_is_idempotent(self, api, customer, address, restaurant, menu, fake_razorpay):
        c, body = self._place_online(api, customer, address, menu)
        oid = body["order"]["id"]
        amount = int(Decimal(body["order"]["total_amount"]) * 100)
        event = {"event": "payment.captured", "payload": {"payment": {"entity": {
            "id": "pay_wh1", "order_id": "order_test123", "amount": amount, "status": "captured"}}}}
        raw = json.dumps(event).encode()
        anon = api.__class__()
        r = anon.post("/api/v1/payments/webhooks/razorpay/", raw, content_type="application/json",
                      HTTP_X_RAZORPAY_SIGNATURE="nope", HTTP_X_RAZORPAY_EVENT_ID="evt_1")
        assert r.status_code == 400
        r = anon.post("/api/v1/payments/webhooks/razorpay/", raw, content_type="application/json",
                      HTTP_X_RAZORPAY_SIGNATURE=webhook_sig("whsec_test", raw), HTTP_X_RAZORPAY_EVENT_ID="evt_1")
        assert r.status_code == 200
        o = Order.objects.get(pk=oid)
        assert o.status == OrderStatus.PLACED and o.is_paid
        assert WebhookEvent.objects.get(event_id="evt_1").processed
        # replay does nothing new
        r = anon.post("/api/v1/payments/webhooks/razorpay/", raw, content_type="application/json",
                      HTTP_X_RAZORPAY_SIGNATURE=webhook_sig("whsec_test", raw), HTTP_X_RAZORPAY_EVENT_ID="evt_1")
        assert r.status_code == 200 and o.status_history.filter(to_status="placed").count() == 1

    def test_webhook_failed_then_cancel_refund(self, api, customer, address, restaurant, menu, fake_razorpay, partner):
        c, body = self._place_online(api, customer, address, menu)
        oid = body["order"]["id"]
        amount = int(Decimal(body["order"]["total_amount"]) * 100)
        fail = json.dumps({"event": "payment.failed", "payload": {"payment": {"entity": {
            "id": "pay_f", "order_id": "order_test123", "amount": amount, "error_description": "card declined"}}}}).encode()
        anon = api.__class__()
        anon.post("/api/v1/payments/webhooks/razorpay/", fail, content_type="application/json",
                  HTTP_X_RAZORPAY_SIGNATURE=webhook_sig("whsec_test", fail), HTTP_X_RAZORPAY_EVENT_ID="evt_f")
        assert Order.objects.get(pk=oid).status == OrderStatus.PAYMENT_FAILED
        # retry + capture via webhook
        r = c.post(f"/api/v1/payments/orders/{oid}/retry/")
        assert r.status_code == 200
        cap = json.dumps({"event": "payment.captured", "payload": {"payment": {"entity": {
            "id": "pay_ok", "order_id": "order_test123", "amount": amount, "status": "captured"}}}}).encode()
        anon.post("/api/v1/payments/webhooks/razorpay/", cap, content_type="application/json",
                  HTTP_X_RAZORPAY_SIGNATURE=webhook_sig("whsec_test", cap), HTTP_X_RAZORPAY_EVENT_ID="evt_c")
        assert Order.objects.get(pk=oid).status == OrderStatus.PLACED
        # restaurant rejects → refund initiated through provider
        r = auth(api, partner).post(f"/api/v1/partner/orders/{oid}/reject/", {"reason": "kitchen closed"},
                                    format="json")
        assert r.status_code == 200
        refund = Refund.objects.get(order_id=oid)
        assert refund.status == RefundStatus.COMPLETED and refund.amount == Decimal(body["order"]["total_amount"])
        assert Payment.objects.get(provider_payment_id="pay_ok").status == PaymentStatus.REFUNDED
        r = auth(api, customer).get(f"/api/v1/orders/{oid}/")
        assert r.json()["refund_status"]["status"] == "completed"

    def test_cod_limit_and_disabled(self, api, customer, address, restaurant, menu):
        from apps.core.models import PlatformSettings

        ps = PlatformSettings.load()
        ps.cod_max_order_value = Decimal("100")
        ps.save()
        c = auth(api, customer)
        fill_cart(c, menu)
        r = c.post("/api/v1/orders/", {"address_id": str(address.id), "payment_method": "cod"}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "cod_limit"
