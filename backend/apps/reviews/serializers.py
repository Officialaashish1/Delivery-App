from rest_framework import serializers

from .models import Review


class ReviewSerializer(serializers.ModelSerializer):
    customer_name = serializers.SerializerMethodField()
    order_number = serializers.CharField(source="order.order_number", read_only=True)

    class Meta:
        model = Review
        fields = ("id", "order", "order_number", "customer_name", "restaurant", "restaurant_rating",
                  "restaurant_comment", "rider_rating", "rider_comment", "item_ratings", "tags", "partner_reply",
                  "partner_replied_at", "created_at")
        read_only_fields = ("id", "order", "restaurant", "partner_reply", "partner_replied_at", "created_at")

    def get_customer_name(self, obj):
        name = obj.customer.full_name or "Customer"
        parts = name.split()
        return parts[0] + (f" {parts[-1][0]}." if len(parts) > 1 else "")


class CreateReviewSerializer(serializers.Serializer):
    order_id = serializers.UUIDField()
    restaurant_rating = serializers.IntegerField(min_value=1, max_value=5)
    restaurant_comment = serializers.CharField(required=False, allow_blank=True, max_length=1000, default="")
    rider_rating = serializers.IntegerField(min_value=1, max_value=5, required=False, allow_null=True)
    rider_comment = serializers.CharField(required=False, allow_blank=True, max_length=1000, default="")
    item_ratings = serializers.DictField(child=serializers.IntegerField(min_value=1, max_value=5), required=False)
    tags = serializers.ListField(child=serializers.CharField(max_length=30), required=False, default=list)
