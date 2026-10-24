"""Firebase Cloud Messaging sender. Disabled gracefully when FIREBASE_CREDENTIALS_FILE is not configured."""
import logging
import os

from django.conf import settings

log = logging.getLogger("localrush.fcm")
_app = None


def _init():
    global _app
    if _app is not None:
        return _app
    path = settings.LOCALRUSH.get("FIREBASE_CREDENTIALS_FILE")
    if not path or not os.path.exists(path):
        return None
    import firebase_admin
    from firebase_admin import credentials

    _app = firebase_admin.initialize_app(credentials.Certificate(path))
    return _app


def send_to_tokens(tokens: list[str], title: str, body: str, data: dict, channel_id="localrush_orders",
                   high_priority=True) -> dict:
    """Returns {"sent": n, "invalid_tokens": [...]}"""
    if not tokens:
        return {"sent": 0, "invalid_tokens": []}
    if _init() is None:
        log.info("[FCM disabled] %s: %s -> %d tokens", title, body, len(tokens))
        return {"sent": 0, "invalid_tokens": []}
    from firebase_admin import messaging

    str_data = {k: str(v) for k, v in (data or {}).items()}
    str_data.setdefault("title", title)
    str_data.setdefault("body", body)
    message = messaging.MulticastMessage(
        tokens=tokens,
        notification=messaging.Notification(title=title, body=body),
        data=str_data,
        android=messaging.AndroidConfig(
            priority="high" if high_priority else "normal", ttl=3600,
            notification=messaging.AndroidNotification(channel_id=channel_id, sound="default",
                                                       click_action="OPEN_FROM_NOTIFICATION")),
    )
    resp = messaging.send_each_for_multicast(message)
    invalid = []
    for tok, r in zip(tokens, resp.responses, strict=False):
        if not r.success and r.exception is not None:
            code = getattr(r.exception, "code", "") or str(r.exception)
            if "registration-token-not-registered" in str(code) or "invalid-argument" in str(code).lower():
                invalid.append(tok)
    return {"sent": resp.success_count, "invalid_tokens": invalid}
