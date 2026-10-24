from django.urls import include, path
from rest_framework.routers import DefaultRouter

from . import views

router = DefaultRouter()
router.register("tickets", views.TicketViewSet, basename="ticket")

urlpatterns = [
    path("faqs/", views.FAQListView.as_view(), name="faq-list"),
    path("contact/", views.SupportContactView.as_view(), name="support-contact"),
    path("", include(router.urls)),
]
