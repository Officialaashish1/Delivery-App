from django.utils import timezone
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import generics, permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.core.permissions import IsCustomer, IsPartner

from .models import Review
from .serializers import CreateReviewSerializer, ReviewSerializer
from .services import create_review


@extend_schema(tags=["reviews"])
class ReviewCreateView(APIView):
    permission_classes = [IsCustomer]

    @extend_schema(request=CreateReviewSerializer, responses=ReviewSerializer)
    def post(self, request):
        ser = CreateReviewSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        review = create_review(request.user, d.pop("order_id"), **d)
        return Response(ReviewSerializer(review).data, status=status.HTTP_201_CREATED)


@extend_schema(tags=["reviews"], parameters=[OpenApiParameter("restaurant", str, required=True)])
class RestaurantReviewListView(generics.ListAPIView):
    permission_classes = [permissions.AllowAny]
    serializer_class = ReviewSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return Review.objects.none()
        rid = self.request.query_params.get("restaurant")
        return Review.objects.filter(restaurant_id=rid, is_visible=True).select_related("customer", "order")


@extend_schema(tags=["reviews"])
class MyReviewsView(generics.ListAPIView):
    permission_classes = [IsCustomer]
    serializer_class = ReviewSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return Review.objects.none()
        return Review.objects.filter(customer=self.request.user).select_related("customer", "order")


@extend_schema(tags=["partner"])
class PartnerReviewListView(generics.ListAPIView):
    permission_classes = [IsPartner]
    serializer_class = ReviewSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return Review.objects.none()
        from apps.restaurants.views import get_own_restaurant

        return Review.objects.filter(restaurant=get_own_restaurant(self.request)).select_related("customer", "order")


@extend_schema(tags=["partner"])
class PartnerReviewReplyView(APIView):
    permission_classes = [IsPartner]

    def post(self, request, pk):
        from apps.restaurants.views import get_own_restaurant

        review = Review.objects.filter(pk=pk, restaurant=get_own_restaurant(request)).first()
        if review is None:
            return Response(status=404)
        review.partner_reply = (request.data.get("reply") or "")[:1000]
        review.partner_replied_at = timezone.now()
        review.save(update_fields=["partner_reply", "partner_replied_at"])
        return Response(ReviewSerializer(review).data)
