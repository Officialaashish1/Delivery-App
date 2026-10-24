from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .. import views

router = DefaultRouter()
router.register("", views.RestaurantViewSet, basename="restaurant")

urlpatterns = [
    path("cuisines/", views.CuisineListView.as_view(), name="cuisine-list"),
    path("categories/", views.CategoryListView.as_view(), name="category-list"),
    path("", include(router.urls)),
]
