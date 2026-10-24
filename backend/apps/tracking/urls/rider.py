from django.urls import path

from .. import views

urlpatterns = [path("location/", views.RiderLocationView.as_view(), name="rider-location")]
