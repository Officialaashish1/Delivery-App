from rest_framework import serializers

from apps.core.images import compress_image

from .models import RiderDocument, RiderDocumentType, RiderEarning, RiderPayout, RiderProfile


class RiderProfileSerializer(serializers.ModelSerializer):
    phone = serializers.CharField(source="user.phone", read_only=True)
    is_approved = serializers.BooleanField(read_only=True)
    documents_summary = serializers.SerializerMethodField()
    lat = serializers.SerializerMethodField()
    lng = serializers.SerializerMethodField()

    class Meta:
        model = RiderProfile
        fields = ("id", "phone", "full_name", "photo", "email", "date_of_birth", "address", "city", "vehicle_type",
                  "vehicle_number", "driving_licence_number", "driving_licence_expiry", "aadhaar_last4",
                  "pan_number", "bank_account_name", "bank_account_number", "bank_ifsc", "upi_id",
                  "emergency_contact_name", "emergency_contact_phone", "status", "rejection_reason", "is_approved",
                  "is_online", "is_on_delivery", "rating_avg", "rating_count", "total_deliveries", "wallet_balance",
                  "cod_cash_in_hand", "documents_summary", "lat", "lng", "created_at")
        read_only_fields = ("id", "status", "rejection_reason", "is_online", "is_on_delivery", "rating_avg",
                            "rating_count", "total_deliveries", "wallet_balance", "cod_cash_in_hand", "created_at")

    def get_documents_summary(self, obj):
        return {d.doc_type: d.status for d in obj.documents.all()}

    def get_lat(self, obj):
        return obj.current_location.y if obj.current_location else None

    def get_lng(self, obj):
        return obj.current_location.x if obj.current_location else None

    def validate(self, attrs):
        if attrs.get("photo"):
            attrs["photo"] = compress_image(attrs["photo"], max_side=640)
        if self.instance and self.instance.is_approved:
            locked = {"bank_account_number", "bank_ifsc", "bank_account_name", "upi_id", "pan_number",
                      "driving_licence_number", "vehicle_number"}
            if locked & set(attrs.keys()):
                raise serializers.ValidationError("Verified details are locked. Contact support to change them.")
        return attrs

    def update(self, instance, validated):
        instance = super().update(instance, validated)
        if validated.get("full_name") and instance.user.full_name != validated["full_name"]:
            instance.user.full_name = validated["full_name"]
            instance.user.save(update_fields=["full_name"])
        if instance.status == "rejected":
            instance.status = "pending"
            instance.rejection_reason = ""
            instance.save(update_fields=["status", "rejection_reason"])
        return instance


class RiderDocumentSerializer(serializers.ModelSerializer):
    doc_type = serializers.ChoiceField(choices=RiderDocumentType.choices)

    class Meta:
        model = RiderDocument
        fields = ("id", "doc_type", "file", "number", "status", "rejection_reason", "verified_at", "created_at")
        read_only_fields = ("id", "status", "rejection_reason", "verified_at", "created_at")


class RiderEarningSerializer(serializers.ModelSerializer):
    order_number = serializers.CharField(source="order.order_number", read_only=True, default=None)

    class Meta:
        model = RiderEarning
        fields = ("id", "earning_type", "amount", "description", "distance_km", "order", "order_number",
                  "is_settled", "created_at")


class RiderPayoutSerializer(serializers.ModelSerializer):
    class Meta:
        model = RiderPayout
        fields = ("id", "amount", "status", "period_start", "period_end", "reference", "note", "processed_at",
                  "created_at")
        read_only_fields = fields


class PayoutRequestSerializer(serializers.Serializer):
    amount = serializers.DecimalField(max_digits=12, decimal_places=2, required=False)


class OnlineToggleSerializer(serializers.Serializer):
    is_online = serializers.BooleanField()
    latitude = serializers.FloatField(required=False)
    longitude = serializers.FloatField(required=False)


class RiderOTPActionSerializer(serializers.Serializer):
    otp = serializers.RegexField(r"^\d{4,6}$")


class RiderReasonSerializer(serializers.Serializer):
    reason = serializers.CharField(max_length=255)
