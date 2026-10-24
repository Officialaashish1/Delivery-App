"""
SMS provider abstraction for OTP delivery. Select with SMS_PROVIDER env:
  console  - logs the OTP (development only)
  msg91    - MSG91 OTP API (India). Needs MSG91_AUTH_KEY, MSG91_TEMPLATE_ID
  twilio   - Twilio Programmable SMS. Needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER
"""
import logging

import requests
from django.conf import settings

log = logging.getLogger("localrush.sms")


class SMSError(Exception):
    pass


class ConsoleSMS:
    def send_otp(self, phone: str, code: str):
        log.info("[DEV SMS] OTP for %s is %s", phone, code)


class Msg91SMS:
    def send_otp(self, phone: str, code: str):
        cfg = settings.LOCALRUSH
        r = requests.post(
            "https://control.msg91.com/api/v5/otp",
            params={"template_id": cfg["MSG91_TEMPLATE_ID"], "mobile": phone.lstrip("+"), "otp": code},
            headers={"authkey": cfg["MSG91_AUTH_KEY"]},
            timeout=8,
        )
        if r.status_code >= 300 or r.json().get("type") != "success":
            raise SMSError(f"MSG91 failed: {r.text[:200]}")


class TwilioSMS:
    def send_otp(self, phone: str, code: str):
        cfg = settings.LOCALRUSH
        r = requests.post(
            f"https://api.twilio.com/2010-04-01/Accounts/{cfg['TWILIO_ACCOUNT_SID']}/Messages.json",
            data={"To": phone, "From": cfg["TWILIO_FROM_NUMBER"],
                  "Body": f"{code} is your LocalRush verification code. Valid for 5 minutes."},
            auth=(cfg["TWILIO_ACCOUNT_SID"], cfg["TWILIO_AUTH_TOKEN"]),
            timeout=8,
        )
        if r.status_code >= 300:
            raise SMSError(f"Twilio failed: {r.text[:200]}")


def get_sms_provider():
    provider = settings.LOCALRUSH["SMS_PROVIDER"]
    return {"console": ConsoleSMS, "msg91": Msg91SMS, "twilio": TwilioSMS}.get(provider, ConsoleSMS)()
