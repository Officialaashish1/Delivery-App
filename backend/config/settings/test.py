from .base import *  # noqa

DEBUG = False
SECRET_KEY = "test-secret-key-not-for-production"
PASSWORD_HASHERS = ["django.contrib.auth.hashers.MD5PasswordHasher"]
CELERY_TASK_ALWAYS_EAGER = True
CELERY_TASK_EAGER_PROPAGATES = True
CHANNEL_LAYERS = {"default": {"BACKEND": "channels.layers.InMemoryChannelLayer"}}
CACHES = {"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}}
REST_FRAMEWORK["DEFAULT_THROTTLE_CLASSES"] = ()  # noqa: F405
LOCALRUSH.update({  # noqa: F405
    "SMS_PROVIDER": "console",
    "OTP_DEV_FIXED_CODE": "",
    "PAYMENT_PROVIDER": "razorpay",
    "RAZORPAY_KEY_ID": "rzp_test_key",
    "RAZORPAY_KEY_SECRET": "rzp_test_secret",
    "RAZORPAY_WEBHOOK_SECRET": "whsec_test",
    "FIREBASE_CREDENTIALS_FILE": "",
    "OSRM_BASE_URL": "http://osrm.test",
    "NOMINATIM_BASE_URL": "http://nominatim.test",
})
STORAGES["staticfiles"] = {"BACKEND": "django.contrib.staticfiles.storage.StaticFilesStorage"}  # noqa: F405
MEDIA_ROOT = "/tmp/localrush-test-media"
LOGGING["root"]["level"] = "ERROR"  # noqa: F405
LOGGING["loggers"]["django_structlog"] = {"handlers": ["console"], "level": "ERROR", "propagate": False}  # noqa: F405
LOGGING["loggers"]["localrush"]["level"] = "ERROR"  # noqa: F405
