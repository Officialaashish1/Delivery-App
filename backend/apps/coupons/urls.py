from django.urls import path

from . import views

urlpatterns = [path("", views.CouponListView.as_view(), name="coupon-list")]
