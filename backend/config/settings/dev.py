from .base import *  # noqa

DEBUG = True
ALLOWED_HOSTS = ["*"]
CORS_ALLOW_ALL_ORIGINS = True
LOCALRUSH["OTP_DEV_FIXED_CODE"] = env.str("OTP_DEV_FIXED_CODE", default="123456")  # noqa: F405
STORAGES["staticfiles"] = {"BACKEND": "django.contrib.staticfiles.storage.StaticFilesStorage"}  # noqa: F405
