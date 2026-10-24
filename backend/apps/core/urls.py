from django.urls import path

from . import views

urlpatterns = [
    path("", views.AppConfigView.as_view(), name="app-config"),
    path("geocode/search/", views.GeocodeSearchView.as_view(), name="geocode-search"),
    path("geocode/reverse/", views.GeocodeReverseView.as_view(), name="geocode-reverse"),
]
