from django.urls import path

from . import views

urlpatterns = [
    path("", views.CartView.as_view(), name="cart"),
    path("items/<uuid:item_id>/", views.CartItemView.as_view(), name="cart-item"),
]
