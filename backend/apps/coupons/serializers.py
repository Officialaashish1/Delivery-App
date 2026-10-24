from rest_framework import serializers

from .models import Coupon


class CouponSerializer(serializers.ModelSerializer):
    short_text = serializers.CharField(read_only=True)

    class Meta:
        model = Coupon
        fields = ("id", "code", "title", "description", "discount_type", "value", "min_order_amount", "max_discount",
                  "first_order_only", "restaurant", "expires_at", "short_text")


class AvailableCouponSerializer(serializers.Serializer):
    coupon = CouponSerializer()
    applicable = serializers.BooleanField()
    discount = serializers.DecimalField(max_digits=12, decimal_places=2)
    reason = serializers.CharField()
