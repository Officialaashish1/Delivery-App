# LocalRush — project structure

```
LocalRush/
├── README.md                     Overview, quick start, demo accounts
├── STRUCTURE.md                  This file
├── DEVELOPMENT_STATUS.md         Phase-by-phase status, verification log, known limitations
├── docker-compose.yml            Development stack (db, redis, api, worker, beat; `--profile maps` adds osrm + nominatim)
├── .gitignore                    Excludes secrets (.env, firebase json), build output, map data, dist/
│
├── backend/                      Django 5.1 · DRF · Channels · Celery · PostGIS · Redis
│   ├── manage.py
│   ├── Dockerfile                python:3.12-slim + GDAL/GEOS/libmagic, non-root user
│   ├── requirements.txt / requirements-dev.txt
│   ├── pytest.ini · ruff.toml
│   ├── .env.example              Every environment variable with comments (copy to .env)
│   ├── config/
│   │   ├── settings/base.py      Shared settings (DB/PostGIS, Redis cache + channel layer, Celery, JWT, DRF, throttles, LOCALRUSH domain config)
│   │   ├── settings/dev.py · prod.py · test.py
│   │   ├── asgi.py               HTTP + WebSocket router (JWT middleware)
│   │   ├── celery.py · wsgi.py · urls.py (/api/v1/*, /api/docs/, /dashboard/, /django-admin/, /healthz/)
│   ├── apps/
│   │   ├── core/                 PlatformSettings singleton, AuditLog, exceptions (error envelope), permissions, upload validators,
│   │   │                         image compression, money/geo utils, OSRM + Nominatim clients, app-config + geocode endpoints,
│   │   │                         management commands: seed, loadtest_tokens
│   │   ├── accounts/             User (phone, roles, token_version), OTP (hashed, throttled), JWT auth + revocation,
│   │   │                         CustomerProfile, Address (PostGIS), Device (FCM), SMS providers (console/MSG91/Twilio)
│   │   ├── restaurants/          Restaurant, hours, holidays, documents, cuisines, categories, banners;
│   │   │                         customer discovery (home/list/search/detail) and partner onboarding/management API
│   │   ├── menu/                 MenuCategory, MenuItem, variant groups, add-on groups; partner menu CRUD
│   │   ├── cart/                 Server-side single-restaurant cart + customisation validation
│   │   ├── coupons/              Coupon, CouponUsage, validation (limits counted across live orders)
│   │   ├── orders/               pricing.py (single source of money math), Order/OrderItem/StatusHistory/RiderAssignment,
│   │   │                         services.py (state machine, placement, dispatch, rider workflow with OTP lockout,
│   │   │                         cancellation/refunds), customer + partner views, Celery tasks, HTML invoice
│   │   ├── payments/             Razorpay provider, checkout verification, idempotent webhooks, refunds, COD collection
│   │   ├── riders/               RiderProfile/documents/locations/earnings/payouts/incentives; rider API; online toggle
│   │   ├── tracking/             Redis hot store, WebSocket consumers (order/rider/partner/admin), JWT WS middleware,
│   │   │                         location ingest (validation, anti-teleport, near-destination), route refresh, chat
│   │   ├── notifications/        In-app notifications, FCM sender, event→copy mapping, promo campaigns
│   │   ├── reviews/              Reviews + rating aggregation, partner replies
│   │   ├── support/              Tickets, messages, FAQs
│   │   ├── finance/              Settlement lines, restaurant settlements, platform ledger, partner finance API + CSV
│   │   └── dashboard/            Admin REST API (users, restaurants, riders, orders, coupons, settlements, payouts,
│   │                             tickets, banners, campaigns, settings, audit, reports) + web dashboard views
│   ├── templates/dashboard/      login.html, app.html (SPA shell) · templates/orders/invoice.html
│   ├── static/dashboard/         app.js (SPA: all admin pages, live map, WebSocket), app.css
│   ├── tests/                    54 pytest tests: auth, cart/pricing, order lifecycle, payments/webhooks,
│   │                             tracking/WebSockets, admin/partner, security regressions
│   ├── scripts/smoke_e2e.py      Live-stack end-to-end test (customer → restaurant → rider → customer)
│   └── loadtests/locustfile.py   Locust load test (customer browsing + rider GPS writes)
│
├── android/                      One Gradle workspace (Kotlin 2.0, AGP 8.7, Compose, Hilt, Retrofit, Room, MapLibre)
│   ├── BUILD.sh                  Builds inside Docker (ghcr.io/cirruslabs/android-sdk:36) — no local SDK needed
│   ├── README.md · CONVENTIONS.md
│   ├── settings.gradle.kts · build.gradle.kts · gradle/libs.versions.toml
│   ├── core/
│   │   ├── common/               Result/AppError, error-envelope parser, Money, OrderStatus/timeline, polyline, geo utils
│   │   ├── network/              Retrofit + OkHttp + kotlinx.serialization, auth interceptor, single-flight token refresh,
│   │   │                         shared DTOs/APIs, WebSocketClient (reconnect/backoff/ping-pong)
│   │   ├── auth/                 Encrypted token store, SessionManager, AuthRepository (OTP/password/logout/devices)
│   │   ├── designsystem/         Material 3 theme (orange/charcoal), buttons, fields, OTP input, chips, step progress bar,
│   │   │                         loading/empty/error/offline states, shimmer, icons
│   │   ├── maps/                 MapLibre Compose wrapper, markers, route polyline, MarkerAnimator (smooth interpolation),
│   │   │                         camera follow/fit, map icons
│   │   ├── location/             Permissions, fused location flows, battery-aware policy, foreground service base
│   │   ├── push/                 FCM service base, notification channels, deep links, token registration
│   │   └── database/             Room: cached JSON, pending rider locations (offline queue), recent searches
│   ├── feature/auth/             Shared phone/OTP (+password) login flow
│   ├── app-customer/             in.localrush.customer — onboarding, location picker, home, search, restaurant, cart,
│   │                             checkout (COD + Razorpay), orders, live tracking, reviews, profile, support
│   ├── app-rider/                in.localrush.rider — registration, approval gate, dashboard + online toggle,
│   │                             foreground GPS service, offers, navigation with turn-by-turn, OTP pickup/delivery,
│   │                             earnings/payouts, history, support
│   └── app-partner/              in.localrush.partner — registration, approval gate, dashboard, real-time orders board,
│                                 menu management, restaurant settings, finance, reviews, support
│
├── deploy/
│   ├── docker-compose.prod.yml   Production stack: db, redis, migrate, api (gunicorn), ws (daphne), worker, beat,
│   │                             osrm, nominatim, nginx, certbot, backup
│   ├── nginx/                    nginx.conf, conf.d/localrush.conf (TLS, /ws/ upgrade, rate limits), proxy-params.inc
│   ├── scripts/                  prepare-osrm.sh, prepare-nominatim.sh, init-letsencrypt.sh, deploy.sh,
│   │                             backup-db.sh, restore-db.sh
│   ├── monitoring/               Prometheus + Grafana + exporters compose
│   ├── osrm/data/ · nominatim/data/   OSM extracts and built graphs (git-ignored)
│
├── docs/
│   ├── ARCHITECTURE.md           System diagram + request flows + code map
│   ├── ER_DIAGRAM.md             Entity relationship diagram (mermaid)
│   ├── API_CONTRACTS.md          Endpoints, error codes, WebSocket protocol, push payloads (shared by all apps)
│   ├── api/openapi.yml           Generated OpenAPI 3 schema (199 operations)
│   ├── DEPLOYMENT.md             VPS production guide, external services checklist
│   ├── TESTING.md                Test suites, E2E, load test, screenshot tests
│   ├── SECURITY.md               Controls + independent review findings and fixes
│   ├── PLAYSTORE_RELEASE.md      APK/AAB build, signing, Firebase, Play Console checklist
│   ├── DEMO_GUIDE.md             10-minute end-to-end demo on real phones
│   ├── android/DESIGN_BRIEF.md   Screen-by-screen design spec for the three apps
│   └── screenshots/              dashboard/ (14 pages), apps/{customer,rider,partner}/ (Paparazzi renders, tracking GIF,
│                                 marker options, web tracking demo)
│
└── dist/ (git-ignored)           Built debug APKs + download page + tracking web demo served on the LAN
```

## Request path at a glance
Android app → nginx → `api` (gunicorn, REST) / `ws` (daphne, WebSockets) → PostgreSQL/PostGIS + Redis → Celery workers (dispatch, push, settlements) → OSRM / Nominatim (self-hosted) · Razorpay · FCM · SMS.
