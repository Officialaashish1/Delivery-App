from django.urls import include, path
from rest_framework.routers import DefaultRouter

from . import views

router = DefaultRouter()
router.register("documents", views.RiderDocumentViewSet, basename="rider-document")
router.register("offers", views.RiderOfferViewSet, basename="rider-offer")
router.register("orders", views.RiderOrderViewSet, basename="rider-order")
router.register("payouts", views.RiderPayoutViewSet, basename="rider-payout")

urlpatterns = [
    path("profile/", views.RiderProfileView.as_view(), name="rider-profile"),
    path("online/", views.RiderOnlineView.as_view(), name="rider-online"),
    path("dashboard/", views.RiderDashboardView.as_view(), name="rider-dashboard"),
    path("earnings/", views.RiderEarningsView.as_view(), name="rider-earnings"),
    path("", include(router.urls)),
]
