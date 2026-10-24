"""
Payment provider abstraction. Razorpay is the default; add others by implementing the same interface.
No card data ever touches our servers: the Android app uses the Razorpay Checkout SDK, we only create
orders, verify signatures, and reconcile via webhooks.
"""
import hashlib
import hmac
import logging
from decimal import Decimal

from django.conf import settings

log = logging.getLogger("localrush.payments")


class ProviderError(Exception):
    pass


class RazorpayProvider:
    name = "razorpay"

    def __init__(self):
        cfg = settings.LOCALRUSH
        self.key_id = cfg["RAZORPAY_KEY_ID"]
        self.key_secret = cfg["RAZORPAY_KEY_SECRET"]
        self.webhook_secret = cfg["RAZORPAY_WEBHOOK_SECRET"]
        self._client = None

    @property
    def client(self):
        if self._client is None:
            import razorpay

            if not self.key_id or not self.key_secret:
                raise ProviderError("Razorpay keys are not configured (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET).")
            self._client = razorpay.Client(auth=(self.key_id, self.key_secret))
            self._client.set_app_details({"title": "LocalRush", "version": "1.0"})
        return self._client

    def create_order(self, amount: Decimal, currency: str, receipt: str, notes: dict) -> dict:
        data = self.client.order.create({
            "amount": int(amount * 100), "currency": currency, "receipt": receipt, "notes": notes,
            "payment_capture": 1,
        })
        return {"provider_order_id": data["id"], "raw": data}

    def verify_checkout_signature(self, provider_order_id: str, provider_payment_id: str, signature: str) -> bool:
        msg = f"{provider_order_id}|{provider_payment_id}".encode()
        expected = hmac.new(self.key_secret.encode(), msg, hashlib.sha256).hexdigest()
        return hmac.compare_digest(expected, signature or "")

    def verify_webhook_signature(self, body: bytes, signature: str) -> bool:
        if not self.webhook_secret:
            return False
        expected = hmac.new(self.webhook_secret.encode(), body, hashlib.sha256).hexdigest()
        return hmac.compare_digest(expected, signature or "")

    def fetch_payment(self, provider_payment_id: str) -> dict:
        return self.client.payment.fetch(provider_payment_id)

    def refund(self, provider_payment_id: str, amount: Decimal, notes: dict) -> dict:
        data = self.client.payment.refund(provider_payment_id, {"amount": int(amount * 100), "notes": notes,
                                                                "speed": "normal"})
        return {"provider_refund_id": data["id"], "status": data.get("status", "pending"), "raw": data}

    def checkout_payload(self, payment, order) -> dict:
        return {
            "provider": self.name,
            "key_id": self.key_id,
            "provider_order_id": payment.provider_order_id,
            "amount_paise": int(payment.amount * 100),
            "currency": payment.currency,
            "name": "LocalRush",
            "description": f"Order {order.order_number}",
            "prefill": {"contact": order.customer.phone, "email": order.customer.email or "",
                        "name": order.customer.full_name},
            "notes": {"order_id": str(order.id), "order_number": order.order_number},
            "theme_color": "#F26B1D",
            "method_hint": payment.method,
        }


def get_provider():
    provider = settings.LOCALRUSH["PAYMENT_PROVIDER"]
    if provider == "razorpay":
        return RazorpayProvider()
    raise ProviderError(f"Unknown payment provider {provider}")
