from django.urls import path

from . import views

urlpatterns = [
    path("", views.ReviewCreateView.as_view(), name="review-create"),
    path("restaurant/", views.RestaurantReviewListView.as_view(), name="restaurant-reviews"),
    path("mine/", views.MyReviewsView.as_view(), name="my-reviews"),
    path("partner/", views.PartnerReviewListView.as_view(), name="partner-reviews"),
    path("partner/<uuid:pk>/reply/", views.PartnerReviewReplyView.as_view(), name="partner-review-reply"),
]
