from decimal import Decimal

from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import permissions
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.restaurants.models import Restaurant

from .models import Coupon
from .serializers import AvailableCouponSerializer, CouponSerializer
from .services import _live_coupons_for, available_coupons


@extend_schema(tags=["coupons"])
class CouponListView(APIView):
    """Public offers list. With restaurant + auth, returns applicability for the current cart subtotal."""

    permission_classes = [permissions.AllowAny]

    @extend_schema(parameters=[OpenApiParameter("restaurant", str), OpenApiParameter("subtotal", float)],
                   responses=AvailableCouponSerializer(many=True))
    def get(self, request):
        rid = request.query_params.get("restaurant")
        restaurant = Restaurant.objects.filter(id=rid).first() if rid else None
        if restaurant and request.user.is_authenticated:
            subtotal = Decimal(str(request.query_params.get("subtotal") or "0"))
            return Response(AvailableCouponSerializer(available_coupons(request.user, restaurant, subtotal),
                                                      many=True).data)
        qs = _live_coupons_for(restaurant).filter(is_public=True) if restaurant else Coupon.objects.filter(
            is_active=True, is_public=True, restaurant__isnull=True)
        return Response([{"coupon": c, "applicable": True, "discount": "0.00", "reason": ""}
                         for c in CouponSerializer(qs, many=True).data])
