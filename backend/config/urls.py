from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path
from drf_spectacular.views import SpectacularAPIView, SpectacularRedocView, SpectacularSwaggerView


def healthz(_request):
    return JsonResponse({"status": "ok"})


api_v1 = [
    path("auth/", include("apps.accounts.urls.auth")),
    path("customers/", include("apps.accounts.urls.customers")),
    path("restaurants/", include("apps.restaurants.urls.public")),
    path("partner/", include("apps.restaurants.urls.partner")),
    path("menu/", include("apps.menu.urls")),
    path("cart/", include("apps.cart.urls")),
    path("orders/", include("apps.orders.urls")),
    path("payments/", include("apps.payments.urls")),
    path("riders/", include("apps.riders.urls")),
    path("rider/", include("apps.tracking.urls.rider")),
    path("tracking/", include("apps.tracking.urls.tracking")),
    path("coupons/", include("apps.coupons.urls")),
    path("reviews/", include("apps.reviews.urls")),
    path("notifications/", include("apps.notifications.urls")),
    path("support/", include("apps.support.urls")),
    path("admin/", include("apps.dashboard.api_urls")),
    path("config/", include("apps.core.urls")),
]

urlpatterns = [
    path("healthz/", healthz),
    path("django-admin/", admin.site.urls),
    path("dashboard/", include("apps.dashboard.urls")),
    path("api/v1/", include((api_v1, "v1"), namespace="v1")),
    path("api/schema/", SpectacularAPIView.as_view(), name="schema"),
    path("api/docs/", SpectacularSwaggerView.as_view(url_name="schema"), name="swagger-ui"),
    path("api/redoc/", SpectacularRedocView.as_view(url_name="schema"), name="redoc"),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
