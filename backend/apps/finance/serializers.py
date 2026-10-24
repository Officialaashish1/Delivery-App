from rest_framework import serializers

from .models import RestaurantSettlement, SettlementLine


class SettlementLineSerializer(serializers.ModelSerializer):
    order_number = serializers.CharField(source="order.order_number", read_only=True)

    class Meta:
        model = SettlementLine
        fields = ("id", "order", "order_number", "gross", "commission_percent", "commission", "coupon_deduction",
                  "refund_deduction", "tax_collected", "net", "is_cod", "delivered_at")


class SettlementSerializer(serializers.ModelSerializer):
    restaurant_name = serializers.CharField(source="restaurant.name", read_only=True)

    class Meta:
        model = RestaurantSettlement
        fields = ("id", "restaurant", "restaurant_name", "period_start", "period_end", "order_count", "gross_sales",
                  "commission_amount", "coupon_deductions", "refund_deductions", "adjustments", "net_payable",
                  "status", "reference", "note", "processed_at", "created_at")
