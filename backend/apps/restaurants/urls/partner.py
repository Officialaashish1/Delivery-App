from django.urls import include, path
from rest_framework.routers import DefaultRouter

from apps.menu import views as menu_views
from apps.orders import partner_views as order_views

from .. import views

router = DefaultRouter()
router.register("holidays", views.PartnerHolidayViewSet, basename="partner-holiday")
router.register("documents", views.PartnerDocumentViewSet, basename="partner-document")
router.register("menu/categories", menu_views.PartnerMenuCategoryViewSet, basename="partner-menu-category")
router.register("menu/items", menu_views.PartnerMenuItemViewSet, basename="partner-menu-item")
router.register("orders", order_views.PartnerOrderViewSet, basename="partner-order")

urlpatterns = [
    path("restaurant/", views.PartnerRestaurantView.as_view(), name="partner-restaurant"),
    path("restaurant/toggle/", views.PartnerToggleView.as_view(), name="partner-toggle"),
    path("restaurant/hours/", views.PartnerHoursView.as_view(), name="partner-hours"),
    path("dashboard/", views.PartnerDashboardView.as_view(), name="partner-dashboard"),
    path("best-sellers/", views.PartnerBestSellersView.as_view(), name="partner-best-sellers"),
    path("finance/", include("apps.finance.urls.partner")),
    path("", include(router.urls)),
]
