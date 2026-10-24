import hashlib
import hmac
import secrets
from datetime import timedelta

from django.conf import settings
from django.core.cache import cache
from django.db import transaction
from django.utils import timezone

from apps.core.exceptions import BusinessError

from ..models import OTPPurpose, OTPRequest, UserRole
from .sms import get_sms_provider


def _hash(code: str, phone: str) -> str:
    return hmac.new(settings.SECRET_KEY.encode(), f"{phone}:{code}".encode(), hashlib.sha256).hexdigest()


def generate_code() -> str:
    fixed = settings.LOCALRUSH.get("OTP_DEV_FIXED_CODE")
    if fixed and settings.DEBUG:
        return fixed
    return f"{secrets.randbelow(10**6):06d}"


def request_otp(phone: str, purpose=OTPPurpose.LOGIN, role=UserRole.CUSTOMER, ip=None) -> OTPRequest:
    cfg = settings.LOCALRUSH
    cooldown_key = f"otp:cooldown:{phone}"
    if cache.get(cooldown_key):
        raise BusinessError("Please wait before requesting another code.", code="otp_cooldown", status_code=429)
    hourly_key = f"otp:hourly:{phone}"
    count = cache.get(hourly_key, 0)
    if count >= 8:
        raise BusinessError("Too many OTP requests. Try again later.", code="otp_rate_limited", status_code=429)
    code = generate_code()
    with transaction.atomic():
        OTPRequest.objects.filter(phone=phone, purpose=purpose, is_used=False).update(is_used=True)
        otp = OTPRequest.objects.create(
            phone=phone, purpose=purpose, role=role, code_hash=_hash(code, phone),
            expires_at=timezone.now() + timedelta(seconds=cfg["OTP_EXPIRY_SECONDS"]), ip_address=ip,
        )
    get_sms_provider().send_otp(phone, code)
    cache.set(cooldown_key, 1, 30)
    cache.set(hourly_key, count + 1, 3600)
    return otp


@transaction.atomic
def verify_otp(phone: str, code: str, purpose=OTPPurpose.LOGIN) -> OTPRequest:
    cfg = settings.LOCALRUSH
    otp = OTPRequest.objects.select_for_update().filter(phone=phone, purpose=purpose, is_used=False).order_by(
        "-created_at").first()
    if otp is None or otp.is_expired:
        raise BusinessError("OTP expired or not requested. Please request a new code.", code="otp_expired")
    if otp.attempts >= cfg["OTP_MAX_ATTEMPTS"]:
        otp.is_used = True
        otp.save(update_fields=["is_used"])
        raise BusinessError("Too many incorrect attempts. Request a new code.", code="otp_locked")
    if not hmac.compare_digest(otp.code_hash, _hash(code, phone)):
        otp.attempts += 1
        otp.save(update_fields=["attempts"])
        raise BusinessError("Incorrect OTP.", code="otp_invalid",
                            extra={"attempts_left": cfg["OTP_MAX_ATTEMPTS"] - otp.attempts})
    otp.is_used = True
    otp.save(update_fields=["is_used"])
    return otp
