from django.db import transaction
from django.utils import timezone
from drf_spectacular.utils import extend_schema
from rest_framework import generics, permissions, status, viewsets
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenRefreshView

from apps.core.audit import audit
from apps.core.exceptions import BusinessError
from apps.core.middleware import get_client_ip

from . import serializers as s
from .authentication import issue_tokens
from .models import Address, CustomerProfile, Device, OTPPurpose, User, UserRole
from .services.otp import request_otp, verify_otp


def profile_complete_for(user) -> bool:
    if user.role == UserRole.CUSTOMER:
        return bool(user.full_name)
    if user.role == UserRole.RIDER:
        rp = getattr(user, "rider_profile", None)
        return bool(rp and rp.full_name and rp.vehicle_number)
    if user.role == UserRole.PARTNER:
        return hasattr(user, "restaurants") and user.restaurants.exists()
    return True


def auth_payload(user, is_new=False) -> dict:
    tokens = issue_tokens(user)
    user.last_login = timezone.now()
    user.save(update_fields=["last_login"])
    return {**tokens, "user": s.UserSerializer(user).data, "is_new_user": is_new,
            "profile_complete": profile_complete_for(user)}


class RequestOTPView(APIView):
    """Step 1 of phone auth. Sends an OTP via the configured SMS provider."""

    permission_classes = [permissions.AllowAny]
    authentication_classes = []
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "otp"

    @extend_schema(tags=["auth"], request=s.OTPRequestSerializer, responses={200: dict})
    def post(self, request):
        ser = s.OTPRequestSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        existing = User.objects.filter(phone=d["phone"]).first()
        if existing and existing.is_suspended:
            raise BusinessError("This account is suspended. Contact support.", code="account_suspended", status_code=403)
        if existing and existing.role != d["role"] and d["purpose"] == OTPPurpose.LOGIN:
            raise BusinessError(f"This number is registered as a {existing.get_role_display().lower()} account.",
                                code="wrong_app", status_code=409)
        otp = request_otp(d["phone"], d["purpose"], d["role"], ip=get_client_ip(request))
        return Response({"message": "OTP sent", "expires_in": int((otp.expires_at - timezone.now()).total_seconds()),
                         "is_existing_user": existing is not None})


class VerifyOTPView(APIView):
    """Step 2 of phone auth. Verifies OTP, creates the user if needed, returns JWT pair."""

    permission_classes = [permissions.AllowAny]
    authentication_classes = []
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "otp_verify"

    @extend_schema(tags=["auth"], request=s.OTPVerifySerializer, responses=s.AuthResponseSerializer)
    def post(self, request):
        ser = s.OTPVerifySerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        verify_otp(d["phone"], d["code"], OTPPurpose.LOGIN)
        with transaction.atomic():
            user, created = User.objects.select_for_update().get_or_create(
                phone=d["phone"],
                defaults={"role": d["role"], "full_name": d.get("full_name", ""),
                          "email": d.get("email") or None, "is_phone_verified": True},
            )
            if not created:
                if user.role != d["role"]:
                    raise BusinessError("This number belongs to a different account type.", code="wrong_app",
                                        status_code=409)
                if not user.can_login:
                    raise BusinessError("Account disabled.", code="account_suspended", status_code=403)
                updates = []
                if d.get("full_name") and not user.full_name:
                    user.full_name = d["full_name"]
                    updates.append("full_name")
                if not user.is_phone_verified:
                    user.is_phone_verified = True
                    updates.append("is_phone_verified")
                if updates:
                    user.save(update_fields=updates)
            if user.role == UserRole.CUSTOMER:
                profile, _ = CustomerProfile.objects.get_or_create(user=user)
                if created and d.get("referral_code"):
                    ref = CustomerProfile.objects.filter(referral_code=d["referral_code"].upper()).first()
                    if ref and ref.pk != profile.pk:
                        profile.referred_by = ref
                        profile.save(update_fields=["referred_by"])
            elif user.role == UserRole.RIDER:
                from apps.riders.models import RiderProfile

                RiderProfile.objects.get_or_create(user=user, defaults={"full_name": user.full_name})
        audit("auth.otp_login", target=user, actor=user, created=created)
        return Response(auth_payload(user, is_new=created))


class PasswordLoginView(APIView):
    """Password login (admins/partners who set a password). Customers and riders use OTP."""

    permission_classes = [permissions.AllowAny]
    authentication_classes = []
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "otp_verify"

    @extend_schema(tags=["auth"], request=s.PasswordLoginSerializer, responses=s.AuthResponseSerializer)
    def post(self, request):
        ser = s.PasswordLoginSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        user = ser.validated_data["user"]
        audit("auth.password_login", target=user, actor=user)
        return Response(auth_payload(user))


class RefreshView(TokenRefreshView):
    serializer_class = s.TokenRefreshWithVersionSerializer
    authentication_classes = []

    @extend_schema(tags=["auth"])
    def post(self, request, *args, **kwargs):
        return super().post(request, *args, **kwargs)


class LogoutView(APIView):
    @extend_schema(tags=["auth"], request={"application/json": {"type": "object", "properties": {
        "refresh": {"type": "string"}, "fcm_token": {"type": "string"}}}}, responses={204: None})
    def post(self, request):
        refresh = request.data.get("refresh")
        if refresh:
            try:
                RefreshToken(refresh).blacklist()
            except TokenError:
                pass
        if request.data.get("fcm_token"):
            Device.objects.filter(fcm_token=request.data["fcm_token"], user=request.user).update(is_active=False)
        return Response(status=status.HTTP_204_NO_CONTENT)


class LogoutAllView(APIView):
    @extend_schema(tags=["auth"], responses={204: None})
    def post(self, request):
        request.user.revoke_all_tokens()
        Device.objects.filter(user=request.user).update(is_active=False)
        audit("auth.logout_all", target=request.user)
        return Response(status=status.HTTP_204_NO_CONTENT)


class ForgotPasswordView(APIView):
    """Reset password via OTP: request with purpose=password_reset, then POST here with code + new password."""

    permission_classes = [permissions.AllowAny]
    authentication_classes = []
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "otp_verify"

    @extend_schema(tags=["auth"], request=s.SetPasswordSerializer, responses={200: dict})
    def post(self, request):
        ser = s.SetPasswordSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        user = User.objects.filter(phone=d["phone"]).first()
        if user is None:
            raise BusinessError("No account found for this number.", code="not_found", status_code=404)
        verify_otp(d["phone"], d["code"], OTPPurpose.PASSWORD_RESET)
        user.set_password(d["new_password"])
        user.save(update_fields=["password"])
        user.revoke_all_tokens()
        audit("auth.password_reset", target=user, actor=user)
        return Response({"message": "Password updated. Please login again."})


class ChangePasswordView(APIView):
    @extend_schema(tags=["auth"], request=s.ChangePasswordSerializer, responses={200: dict})
    def post(self, request):
        ser = s.ChangePasswordSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        if request.user.has_usable_password() and not request.user.check_password(ser.validated_data["current_password"]):
            raise BusinessError("Current password is incorrect.", code="invalid_credentials")
        request.user.set_password(ser.validated_data["new_password"])
        request.user.save(update_fields=["password"])
        return Response({"message": "Password changed."})


class MeView(generics.RetrieveUpdateAPIView):
    serializer_class = s.UserSerializer

    @extend_schema(tags=["auth"])
    def get(self, request, *args, **kwargs):
        return super().get(request, *args, **kwargs)

    def get_object(self):
        return self.request.user


class DeviceRegisterView(generics.CreateAPIView):
    serializer_class = s.DeviceSerializer

    @extend_schema(tags=["auth"])
    def post(self, request, *args, **kwargs):
        return super().post(request, *args, **kwargs)


class DeleteAccountView(APIView):
    """Soft account deletion required by Play Store policy: anonymises PII and revokes access."""

    @extend_schema(tags=["auth"], responses={204: None})
    def delete(self, request):
        user = request.user
        from apps.orders.models import ACTIVE_STATUSES, Order

        if user.role == UserRole.RIDER and Order.objects.filter(rider__user=user, status__in=ACTIVE_STATUSES).exists():
            raise BusinessError("Finish your active delivery before deleting the account.", code="delivery_in_progress")
        if user.role == UserRole.PARTNER and Order.objects.filter(restaurant__owner=user,
                                                                 status__in=ACTIVE_STATUSES).exists():
            raise BusinessError("Complete or cancel live orders before deleting the account.", code="orders_in_progress")
        if user.role == UserRole.CUSTOMER and Order.objects.filter(customer=user, status__in=ACTIVE_STATUSES).exists():
            raise BusinessError("You have an active order. Try again after it is delivered.", code="orders_in_progress")
        user.full_name = "Deleted user"
        user.email = None
        user.is_active = False
        user.phone = f"+0000{str(user.id).replace('-', '')[:11]}"
        user.revoke_all_tokens()
        user.save()
        Device.objects.filter(user=user).update(is_active=False)
        audit("auth.account_deleted", target=user, actor=user)
        return Response(status=status.HTTP_204_NO_CONTENT)


# ------------------------------------------------------------------ customers

class CustomerProfileView(generics.RetrieveUpdateAPIView):
    serializer_class = s.CustomerProfileSerializer

    def get_object(self):
        profile, _ = CustomerProfile.objects.select_related("user").get_or_create(user=self.request.user)
        return profile

    @extend_schema(tags=["customers"])
    def get(self, request, *args, **kwargs):
        return super().get(request, *args, **kwargs)


@extend_schema(tags=["customers"])
class AddressViewSet(viewsets.ModelViewSet):
    serializer_class = s.AddressSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return Address.objects.none()
        return Address.objects.filter(user=self.request.user)

    def perform_destroy(self, instance):
        was_default = instance.is_default
        instance.delete()
        if was_default:
            nxt = Address.objects.filter(user=self.request.user).first()
            if nxt:
                nxt.is_default = True
                nxt.save()
