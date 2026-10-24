from rest_framework import serializers

from .models import FAQ, SupportTicket, TicketMessage


class TicketMessageSerializer(serializers.ModelSerializer):
    sender_name = serializers.SerializerMethodField()

    class Meta:
        model = TicketMessage
        fields = ("id", "sender_name", "is_staff_reply", "text", "attachment", "created_at")
        read_only_fields = ("id", "sender_name", "is_staff_reply", "created_at")

    def get_sender_name(self, obj):
        return "LocalRush Support" if obj.is_staff_reply else (obj.sender.full_name or "You")


class SupportTicketSerializer(serializers.ModelSerializer):
    messages = TicketMessageSerializer(many=True, read_only=True)
    order_number = serializers.CharField(source="order.order_number", read_only=True, default=None)

    class Meta:
        model = SupportTicket
        fields = ("id", "ticket_number", "order", "order_number", "category", "subject", "description", "status",
                  "priority", "resolution", "resolved_at", "refund_amount", "messages", "created_at", "updated_at")
        read_only_fields = ("id", "ticket_number", "status", "priority", "resolution", "resolved_at", "refund_amount",
                            "created_at", "updated_at")

    def validate_order(self, order):
        user = self.context["request"].user
        if order and order.customer_id != user.id:
            rider = getattr(user, "rider_profile", None)
            is_rider = bool(rider and order.rider_id == rider.id)
            is_partner = order.restaurant.owner_id == user.id
            if not (is_rider or is_partner):
                raise serializers.ValidationError("Order does not belong to you.")
        return order


class FAQSerializer(serializers.ModelSerializer):
    class Meta:
        model = FAQ
        fields = ("id", "question", "answer")
