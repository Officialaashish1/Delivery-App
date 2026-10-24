from django.urls import path

from . import views

urlpatterns = [
    path("items/<uuid:pk>/", views.MenuItemDetailView.as_view(), name="menu-item-detail"),
]
