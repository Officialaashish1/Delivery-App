import phonenumbers
from django.contrib.auth import authenticate
from django.contrib.gis.geos import Point
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer, TokenRefreshSerializer
from rest_framework_simplejwt.tokens import RefreshToken

from .models import Address, AppKind, CustomerProfile, Device, DevicePlatform, OTPPurpose, User, UserRole


def normalise_phone(value: str) -> str:
    try:
        parsed = phonenumbers.parse(value, "IN")
    except phonenumbers.NumberParseException:
        raise serializers.ValidationError("Enter a valid mobile number.")
    if not phonenumbers.is_valid_number(parsed):
        raise serializers.ValidationError("Enter a valid mobile number.")
    return phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.E164)


class PhoneField(serializers.CharField):
    def to_internal_value(self, data):
        return normalise_phone(super().to_internal_value(data))


class OTPRequestSerializer(serializers.Serializer):
    phone = PhoneField()
    role = serializers.ChoiceField(choices=[UserRole.CUSTOMER, UserRole.RIDER, UserRole.PARTNER],
                                   default=UserRole.CUSTOMER)
    purpose = serializers.ChoiceField(choices=OTPPurpose.choices, default=OTPPurpose.LOGIN)


class OTPVerifySerializer(serializers.Serializer):
    phone = PhoneField()
    code = serializers.RegexField(r"^\d{4,8}$")
    role = serializers.ChoiceField(choices=[UserRole.CUSTOMER, UserRole.RIDER, UserRole.PARTNER],
                                   default=UserRole.CUSTOMER)
    full_name = serializers.CharField(max_length=120, required=False, allow_blank=True)
    email = serializers.EmailField(required=False, allow_blank=True)
    referral_code = serializers.CharField(required=False, allow_blank=True)
    accepted_terms = serializers.BooleanField(default=True)


class PasswordLoginSerializer(serializers.Serializer):
    phone = PhoneField()
    password = serializers.CharField(write_only=True)
    role = serializers.ChoiceField(choices=UserRole.choices, required=False)

    def validate(self, attrs):
        user = authenticate(phone=attrs["phone"], password=attrs["password"])
        if not user or not user.can_login:
            raise serializers.ValidationError("Invalid credentials or account disabled.", code="invalid_credentials")
        if attrs.get("role") and user.role != attrs["role"]:
            raise serializers.ValidationError("This account cannot sign in to this app.", code="wrong_app")
        attrs["user"] = user
        return attrs


class TokenObtainSerializer(TokenObtainPairSerializer):
    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        token["tv"] = user.token_version
        token["role"] = user.role
        return token


class TokenRefreshWithVersionSerializer(TokenRefreshSerializer):
    def validate(self, attrs):
        refresh = RefreshToken(attrs["refresh"])
        user = User.objects.filter(pk=refresh["user_id"]).first()
        if user is None or not user.can_login or refresh.get("tv") != user.token_version:
            raise serializers.ValidationError("Token revoked.", code="token_revoked")
        data = super().validate(attrs)
        return data


class SetPasswordSerializer(serializers.Serializer):
    phone = PhoneField()
    code = serializers.RegexField(r"^\d{4,8}$")
    new_password = serializers.CharField(min_length=8, write_only=True)


class ChangePasswordSerializer(serializers.Serializer):
    current_password = serializers.CharField(write_only=True)
    new_password = serializers.CharField(min_length=8, write_only=True)


class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ("id", "phone", "email", "full_name", "role", "is_phone_verified", "is_email_verified",
                  "created_at")
        read_only_fields = ("id", "phone", "role", "is_phone_verified", "is_email_verified", "created_at")


class CustomerProfileSerializer(serializers.ModelSerializer):
    user = UserSerializer(read_only=True)
    full_name = serializers.CharField(source="user.full_name", required=False, max_length=120)
    email = serializers.EmailField(source="user.email", required=False, allow_null=True, allow_blank=True)

    class Meta:
        model = CustomerProfile
        fields = ("user", "full_name", "email", "avatar", "date_of_birth", "referral_code", "is_veg_only",
                  "total_orders")
        read_only_fields = ("referral_code", "total_orders")

    def update(self, instance, validated):
        user_data = validated.pop("user", {})
        for k, v in user_data.items():
            setattr(instance.user, k, v or None if k == "email" else v)
        instance.user.save()
        return super().update(instance, validated)


class AddressSerializer(serializers.ModelSerializer):
    latitude = serializers.FloatField(write_only=True)
    longitude = serializers.FloatField(write_only=True)
    lat = serializers.FloatField(source="latitude", read_only=True)
    lng = serializers.FloatField(source="longitude", read_only=True)
    formatted = serializers.CharField(read_only=True)

    class Meta:
        model = Address
        fields = ("id", "label", "name", "contact_name", "contact_phone", "address_line1", "address_line2",
                  "landmark", "city", "state", "postal_code", "latitude", "longitude", "lat", "lng",
                  "is_default", "delivery_instructions", "formatted", "created_at")
        read_only_fields = ("id", "created_at")

    def validate(self, attrs):
        lat = attrs.pop("latitude", None)
        lng = attrs.pop("longitude", None)
        if lat is not None and lng is not None:
            if not (-90 <= lat <= 90 and -180 <= lng <= 180):
                raise serializers.ValidationError("Invalid coordinates.")
            attrs["location"] = Point(lng, lat, srid=4326)
        elif self.instance is None:
            raise serializers.ValidationError("latitude and longitude are required.")
        return attrs

    def create(self, validated):
        validated["user"] = self.context["request"].user
        if not Address.objects.filter(user=validated["user"]).exists():
            validated["is_default"] = True
        return super().create(validated)


class DeviceSerializer(serializers.ModelSerializer):
    class Meta:
        model = Device
        fields = ("id", "fcm_token", "platform", "app", "device_id", "app_version")
        read_only_fields = ("id",)
        extra_kwargs = {"platform": {"default": DevicePlatform.ANDROID}, "app": {"default": AppKind.CUSTOMER}}

    def create(self, validated):
        user = self.context["request"].user
        device, _ = Device.objects.update_or_create(
            fcm_token=validated["fcm_token"],
            defaults={**validated, "user": user, "is_active": True},
        )
        return device


class AuthResponseSerializer(serializers.Serializer):
    access = serializers.CharField()
    refresh = serializers.CharField()
    user = UserSerializer()
    is_new_user = serializers.BooleanField()
    profile_complete = serializers.BooleanField()
