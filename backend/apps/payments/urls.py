from django.urls import path

from . import views

urlpatterns = [
    path("verify/", views.VerifyPaymentView.as_view(), name="payment-verify"),
    path("orders/<uuid:order_id>/retry/", views.RetryPaymentView.as_view(), name="payment-retry"),
    path("orders/<uuid:order_id>/", views.OrderPaymentsView.as_view(), name="order-payments"),
    path("refunds/", views.MyRefundsView.as_view(), name="my-refunds"),
    path("webhooks/razorpay/", views.razorpay_webhook, name="razorpay-webhook"),
]
