from django.urls import path

from .. import views

urlpatterns = [
    path("orders/<uuid:order_id>/", views.OrderTrackingView.as_view(), name="order-tracking"),
    path("orders/<uuid:order_id>/chat/", views.OrderChatView.as_view(), name="order-chat"),
    path("route/", views.RouteView.as_view(), name="route"),
]
