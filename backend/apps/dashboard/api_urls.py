from django.urls import include, path
from rest_framework.routers import DefaultRouter

from . import api_views as v

router = DefaultRouter()
router.register("users", v.AdminUserViewSet, basename="admin-user")
router.register("restaurants", v.AdminRestaurantViewSet, basename="admin-restaurant")
router.register("riders", v.AdminRiderViewSet, basename="admin-rider")
router.register("orders", v.AdminOrderViewSet, basename="admin-order")
router.register("coupons", v.AdminCouponViewSet, basename="admin-coupon")
router.register("settlements", v.AdminSettlementViewSet, basename="admin-settlement")
router.register("rider-payouts", v.AdminRiderPayoutViewSet, basename="admin-rider-payout")
router.register("tickets", v.AdminTicketViewSet, basename="admin-ticket")
router.register("banners", v.BannerAdminViewSet, basename="admin-banner")

urlpatterns = [
    path("overview/", v.OverviewView.as_view(), name="admin-overview"),
    path("sales-series/", v.SalesSeriesView.as_view(), name="admin-sales-series"),
    path("reports/<str:kind>/", v.ReportsView.as_view(), name="admin-reports"),
    path("settings/", v.PlatformSettingsView.as_view(), name="admin-settings"),
    path("audit-logs/", v.AuditLogView.as_view(), name="admin-audit"),
    path("campaigns/", v.CampaignView.as_view(), name="admin-campaigns"),
    path("payments/", v.AdminPaymentsView.as_view(), name="admin-payments"),
    path("users/create/", v.AdminUsersCreateView.as_view(), name="admin-user-create"),
    path("", include(router.urls)),
]
