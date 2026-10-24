from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .. import views

router = DefaultRouter()
router.register("addresses", views.AddressViewSet, basename="address")

urlpatterns = [
    path("profile/", views.CustomerProfileView.as_view(), name="customer-profile"),
    path("", include(router.urls)),
]
