"""
LocalRush Django settings. All environment-specific values come from the environment
(see backend/.env.example). Never hardcode secrets here.
"""
from datetime import timedelta
from pathlib import Path

from environs import Env

env = Env()
env.read_env()  # reads backend/.env if present

BASE_DIR = Path(__file__).resolve().parent.parent.parent

SECRET_KEY = env.str("SECRET_KEY")
DEBUG = env.bool("DEBUG", default=False)
ALLOWED_HOSTS = env.list("ALLOWED_HOSTS", default=["localhost", "127.0.0.1"])
CSRF_TRUSTED_ORIGINS = env.list("CSRF_TRUSTED_ORIGINS", default=[])
SITE_URL = env.str("SITE_URL", default="http://localhost:8000")

INSTALLED_APPS = [
    "daphne",
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "django.contrib.gis",
    "django.contrib.humanize",
    # third party
    "rest_framework",
    "rest_framework_simplejwt.token_blacklist",
    "django_filters",
    "corsheaders",
    "drf_spectacular",
    "channels",
    "django_celery_beat",
    "django_structlog",
    # LocalRush apps
    "apps.core",
    "apps.accounts",
    "apps.restaurants",
    "apps.menu",
    "apps.cart",
    "apps.orders",
    "apps.payments",
    "apps.riders",
    "apps.tracking",
    "apps.coupons",
    "apps.reviews",
    "apps.notifications",
    "apps.support",
    "apps.finance",
    "apps.dashboard",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "whitenoise.middleware.WhiteNoiseMiddleware",
    "corsheaders.middleware.CorsMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
    "django_structlog.middlewares.RequestMiddleware",
    "apps.core.middleware.AuditContextMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [BASE_DIR / "templates"],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
                "apps.dashboard.context_processors.dashboard_settings",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

# ---------------------------------------------------------------- Database
DATABASES = {"default": env.dj_db_url("DATABASE_URL", default="postgis://localrush:localrush@localhost:5432/localrush")}
DATABASES["default"]["ENGINE"] = "django.contrib.gis.db.backends.postgis"
DATABASES["default"]["CONN_MAX_AGE"] = env.int("DB_CONN_MAX_AGE", default=60)
DATABASES["default"]["CONN_HEALTH_CHECKS"] = True
DATABASES["default"].setdefault("OPTIONS", {})
if env.bool("DB_POOL", default=False):  # psycopg connection pool (Django 5.1+); keep CONN_MAX_AGE=0 when pooling
    DATABASES["default"]["OPTIONS"]["pool"] = {"min_size": 2, "max_size": env.int("DB_POOL_MAX", default=10)}
    DATABASES["default"]["CONN_MAX_AGE"] = 0

# ---------------------------------------------------------------- Cache / Redis
REDIS_URL = env.str("REDIS_URL", default="redis://localhost:6379/0")
CACHES = {
    "default": {
        "BACKEND": "django_redis.cache.RedisCache",
        "LOCATION": REDIS_URL,
        "OPTIONS": {"CLIENT_CLASS": "django_redis.client.DefaultClient"},
        "KEY_PREFIX": "lr",
    }
}
CHANNEL_LAYERS = {
    "default": {
        "BACKEND": "channels_redis.core.RedisChannelLayer",
        "CONFIG": {"hosts": [REDIS_URL], "capacity": 2000, "expiry": 30},
    }
}

# ---------------------------------------------------------------- Celery
CELERY_BROKER_URL = env.str("CELERY_BROKER_URL", default=REDIS_URL)
CELERY_RESULT_BACKEND = env.str("CELERY_RESULT_BACKEND", default=REDIS_URL)
CELERY_TASK_ALWAYS_EAGER = False
CELERY_TASK_ACKS_LATE = True
CELERY_WORKER_PREFETCH_MULTIPLIER = 1
CELERY_TASK_DEFAULT_QUEUE = "default"
CELERY_TASK_ROUTES = {
    "apps.notifications.*": {"queue": "notifications"},
    "apps.tracking.*": {"queue": "tracking"},
}
CELERY_BEAT_SCHEDULER = "django_celery_beat.schedulers:DatabaseScheduler"
CELERY_TIMEZONE = "Asia/Kolkata"
CELERY_BEAT_SCHEDULE = {
    "expire-rider-offers": {"task": "apps.orders.tasks.expire_stale_rider_offers", "schedule": 15.0},
    "retry-unassigned-orders": {"task": "apps.orders.tasks.retry_unassigned_orders", "schedule": 30.0},
    "auto-reject-unaccepted-orders": {"task": "apps.orders.tasks.auto_reject_unaccepted_orders", "schedule": 60.0},
    "flush-rider-location-history": {"task": "apps.tracking.tasks.flush_location_history", "schedule": 60.0},
    "mark-stale-riders-offline": {"task": "apps.riders.tasks.mark_stale_riders_offline", "schedule": 120.0},
    "generate-daily-settlements": {"task": "apps.finance.tasks.generate_daily_settlements", "schedule": 3600.0},
    "cleanup-expired-tokens": {"task": "apps.accounts.tasks.flush_expired_tokens", "schedule": 86400.0},
    "expire-pending-payments": {"task": "apps.orders.tasks.expire_pending_payments", "schedule": 300.0},
}

# ---------------------------------------------------------------- Auth
AUTH_USER_MODEL = "accounts.User"
AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator", "OPTIONS": {"min_length": 8}},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]
PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.Argon2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2PasswordHasher",
]
LOGIN_URL = "/dashboard/login/"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": ("apps.accounts.authentication.JWTAuthentication",),
    "DEFAULT_PERMISSION_CLASSES": ("rest_framework.permissions.IsAuthenticated",),
    "DEFAULT_RENDERER_CLASSES": ("rest_framework.renderers.JSONRenderer",),
    "DEFAULT_PARSER_CLASSES": (
        "rest_framework.parsers.JSONParser",
        "rest_framework.parsers.MultiPartParser",
        "rest_framework.parsers.FormParser",
    ),
    "DEFAULT_FILTER_BACKENDS": ("django_filters.rest_framework.DjangoFilterBackend",),
    "DEFAULT_PAGINATION_CLASS": "apps.core.pagination.StandardPagination",
    "PAGE_SIZE": 20,
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    "DEFAULT_THROTTLE_CLASSES": (
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
    ),
    "DEFAULT_THROTTLE_RATES": {
        "anon": "60/min",
        "user": "600/min",
        "otp": "5/min",
        "otp_verify": "10/min",
        "location": "60/min",
        "payment": "30/min",
        "rider_otp": "10/min",
        "geo": "60/min",
    },
    "EXCEPTION_HANDLER": "apps.core.exceptions.api_exception_handler",
    "DEFAULT_VERSIONING_CLASS": None,
    # One trusted proxy (nginx) in front: DRF throttles key on the proxy-set X-Forwarded-For, never a client-supplied one.
    "NUM_PROXIES": env.int("NUM_PROXIES", default=1),
}

SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=env.int("JWT_ACCESS_MINUTES", default=30)),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=env.int("JWT_REFRESH_DAYS", default=30)),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
    "UPDATE_LAST_LOGIN": True,
    "AUTH_HEADER_TYPES": ("Bearer",),
    "USER_ID_CLAIM": "user_id",
    "TOKEN_OBTAIN_SERIALIZER": "apps.accounts.serializers.TokenObtainSerializer",
}

SPECTACULAR_SETTINGS = {
    "TITLE": "LocalRush API",
    "DESCRIPTION": "Food delivery platform API used by the Customer, Rider, Partner apps and Admin dashboard.",
    "VERSION": "1.0.0",
    "SERVE_INCLUDE_SCHEMA": False,
    "SCHEMA_PATH_PREFIX": r"/api/v1",
    "COMPONENT_SPLIT_REQUEST": True,
    "SWAGGER_UI_SETTINGS": {"persistAuthorization": True},
    "TAGS": [
        {"name": "auth"}, {"name": "customers"}, {"name": "restaurants"}, {"name": "menu"},
        {"name": "cart"}, {"name": "orders"}, {"name": "payments"}, {"name": "riders"},
        {"name": "tracking"}, {"name": "coupons"}, {"name": "reviews"}, {"name": "notifications"},
        {"name": "support"}, {"name": "partner"}, {"name": "admin"},
    ],
}

CORS_ALLOWED_ORIGINS = env.list("CORS_ALLOWED_ORIGINS", default=[])
CORS_ALLOW_CREDENTIALS = True

# ---------------------------------------------------------------- i18n
LANGUAGE_CODE = "en-in"
TIME_ZONE = "Asia/Kolkata"
USE_I18N = True
USE_TZ = True

# ---------------------------------------------------------------- Static / media
STATIC_URL = "/static/"
STATIC_ROOT = BASE_DIR / "staticfiles"
STATICFILES_DIRS = [BASE_DIR / "static"]
MEDIA_URL = env.str("MEDIA_URL", default="/media/")
MEDIA_ROOT = BASE_DIR / "media"
STORAGES = {
    "default": {"BACKEND": "django.core.files.storage.FileSystemStorage"},
    "staticfiles": {"BACKEND": "whitenoise.storage.CompressedManifestStaticFilesStorage"},
}
if env.str("STORAGE_BACKEND", default="local") == "s3":
    STORAGES["default"] = {
        "BACKEND": "storages.backends.s3.S3Storage",
        "OPTIONS": {
            "bucket_name": env.str("AWS_STORAGE_BUCKET_NAME"),
            "endpoint_url": env.str("AWS_S3_ENDPOINT_URL", default=None),
            "access_key": env.str("AWS_ACCESS_KEY_ID"),
            "secret_key": env.str("AWS_SECRET_ACCESS_KEY"),
            "default_acl": "public-read",
            "querystring_auth": False,
        },
    }
DATA_UPLOAD_MAX_MEMORY_SIZE = 10 * 1024 * 1024
FILE_UPLOAD_MAX_MEMORY_SIZE = 10 * 1024 * 1024
MAX_UPLOAD_SIZE = env.int("MAX_UPLOAD_SIZE", default=5 * 1024 * 1024)
ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp"}
ALLOWED_DOCUMENT_TYPES = {"image/jpeg", "image/png", "image/webp", "application/pdf"}

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# ---------------------------------------------------------------- LocalRush domain settings
LOCALRUSH = {
    "SMS_PROVIDER": env.str("SMS_PROVIDER", default="console"),
    "MSG91_AUTH_KEY": env.str("MSG91_AUTH_KEY", default=""),
    "MSG91_TEMPLATE_ID": env.str("MSG91_TEMPLATE_ID", default=""),
    "TWILIO_ACCOUNT_SID": env.str("TWILIO_ACCOUNT_SID", default=""),
    "TWILIO_AUTH_TOKEN": env.str("TWILIO_AUTH_TOKEN", default=""),
    "TWILIO_FROM_NUMBER": env.str("TWILIO_FROM_NUMBER", default=""),
    "OTP_EXPIRY_SECONDS": env.int("OTP_EXPIRY_SECONDS", default=300),
    "OTP_MAX_ATTEMPTS": env.int("OTP_MAX_ATTEMPTS", default=5),
    "OTP_DEV_FIXED_CODE": env.str("OTP_DEV_FIXED_CODE", default=""),  # dev only, blank in prod
    "PAYMENT_PROVIDER": env.str("PAYMENT_PROVIDER", default="razorpay"),
    "RAZORPAY_KEY_ID": env.str("RAZORPAY_KEY_ID", default=""),
    "RAZORPAY_KEY_SECRET": env.str("RAZORPAY_KEY_SECRET", default=""),
    "RAZORPAY_WEBHOOK_SECRET": env.str("RAZORPAY_WEBHOOK_SECRET", default=""),
    "FIREBASE_CREDENTIALS_FILE": env.str("FIREBASE_CREDENTIALS_FILE", default=""),
    "OSRM_BASE_URL": env.str("OSRM_BASE_URL", default="http://localhost:5000"),
    "NOMINATIM_BASE_URL": env.str("NOMINATIM_BASE_URL", default="http://localhost:8080"),
    "MAP_STYLE_URL": env.str("MAP_STYLE_URL", default="https://tiles.openfreemap.org/styles/liberty"),
    "MAP_ATTRIBUTION": env.str("MAP_ATTRIBUTION", default="© OpenStreetMap contributors, © OpenFreeMap"),
    "CURRENCY": "INR",
    "LOCATION_MIN_INTERVAL_SECONDS": 2,
    "LOCATION_MAX_SPEED_KMPH": 120,
    "RIDER_OFFER_TIMEOUT_SECONDS": env.int("RIDER_OFFER_TIMEOUT_SECONDS", default=45),
    "RIDER_SEARCH_RADIUS_KM": env.float("RIDER_SEARCH_RADIUS_KM", default=6.0),
    "RESTAURANT_ACCEPT_TIMEOUT_MINUTES": env.int("RESTAURANT_ACCEPT_TIMEOUT_MINUTES", default=10),
}

# ---------------------------------------------------------------- Security
SECURE_BROWSER_XSS_FILTER = True
SECURE_CONTENT_TYPE_NOSNIFF = True
X_FRAME_OPTIONS = "DENY"
SESSION_COOKIE_HTTPONLY = True
CSRF_COOKIE_HTTPONLY = False

# ---------------------------------------------------------------- Logging
LOG_LEVEL = env.str("LOG_LEVEL", default="INFO")
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "json": {"()": "django_structlog.formatters.JSONFormatter" if False else "logging.Formatter",
                 "format": "%(asctime)s %(levelname)s %(name)s %(message)s"},
    },
    "handlers": {"console": {"class": "logging.StreamHandler", "formatter": "json"}},
    "root": {"handlers": ["console"], "level": LOG_LEVEL},
    "loggers": {
        "django.request": {"handlers": ["console"], "level": "WARNING", "propagate": False},
        "localrush": {"handlers": ["console"], "level": LOG_LEVEL, "propagate": False},
    },
}

SENTRY_DSN = env.str("SENTRY_DSN", default="")
if SENTRY_DSN:
    import sentry_sdk
    from sentry_sdk.integrations.celery import CeleryIntegration
    from sentry_sdk.integrations.django import DjangoIntegration

    sentry_sdk.init(dsn=SENTRY_DSN, integrations=[DjangoIntegration(), CeleryIntegration()],
                    traces_sample_rate=env.float("SENTRY_TRACES_RATE", default=0.05), send_default_pii=False)
