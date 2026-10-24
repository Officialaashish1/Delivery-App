# LocalRush — complete food delivery platform

Swiggy/Zomato-style platform: **Customer**, **Rider** and **Restaurant Partner** Android apps (Kotlin, Jetpack Compose, MapLibre), a **Django** backend (DRF, Channels WebSockets, Celery, PostGIS, Redis, Razorpay, FCM), self-hosted **OSRM** routing and **Nominatim** geocoding, and a web **admin dashboard**.

![tracking](docs/screenshots/apps/customer/tracking-live-demo.gif)

## What is included
| Component | Highlights |
|---|---|
| Backend (`backend/`) | Phone OTP + JWT auth with revocation, restaurant discovery (PostGIS), menus with variants/add-ons, server-side cart + pricing engine, coupons, order state machine, rider dispatch (nearest online rider, offers with timeout/retry), Razorpay checkout + idempotent webhooks + refunds, OTP-verified pickup/delivery, live GPS tracking (validation, anti-spoof, Redis hot store, OSRM route refresh, auto "near destination"), WebSockets for customer/rider/partner/admin, chat, FCM notifications, reviews, support tickets, settlements/payouts/ledger, audit log. 54 tests + live E2E + load test. |
| Customer app (`android/app-customer`) | Onboarding, location picker (reverse geocode), home (banners, categories, nearby/popular/top-rated), search & filters, restaurant details with customisation, cart + coupons, checkout (COD / UPI / card / net banking via Razorpay), orders, **live tracking** with animated scooter marker and solid route, rider call/chat, delivery OTP, ratings, invoices, support, profile. |
| Rider app (`android/app-rider`) | Registration with documents, admin approval gate, online toggle with foreground GPS service (battery-aware, offline queue + batch replay), full-screen offers with countdown, MapLibre navigation with turn-by-turn + voice, OTP pickup/delivery + proof photo, earnings/incentives/payouts, history, problem reporting. |
| Partner app (`android/app-partner`) | Registration with map pin + documents, approval gate, dashboard, real-time orders board with loud new-order alert (accept/reject/preparing/ready, pickup OTP, rider card), menu management (categories, items, variants, add-ons, stock, bulk availability), hours/holidays/closure, finance (settlements, CSV statements), reviews with replies, support. |
| Admin dashboard (`/dashboard/`) | Overview + sales chart + live rider map, orders (detail, assign/reassign rider, cancel, refund), customers, restaurant & rider approvals with document verification, finance (settlements, payouts, payments, refunds), promotions (coupons, banners, push campaigns), support tickets, reports, platform settings, audit log. |
| Deployment (`deploy/`) | Production Docker Compose (gunicorn + daphne + Celery + PostGIS + Redis + OSRM + Nominatim), nginx with Let's Encrypt, backups, monitoring (Prometheus/Grafana). |

Maps: MapLibre Native + OpenFreeMap tiles (OSM data), OSRM routing and Nominatim geocoding self-hosted — no Google Maps, no paid map SDKs.

## Quick start (development)
Requirements: Docker + Compose v2 (nothing else; the Android build also runs in Docker).
```bash
git clone https://github.com/Officialaashish1/Delivery-App.git LocalRush && cd LocalRush
cp backend/.env.example backend/.env          # dev defaults work; OTP is fixed to 123456 in DEBUG
docker compose up -d                          # db, redis, api (http://localhost:8001), worker, beat
docker compose exec api python manage.py seed --demo
```
- API docs: http://localhost:8001/api/docs/ · Admin dashboard: http://localhost:8001/dashboard/ (`+919999999999` / `admin12345`)
- Demo accounts: partner `+919000000001` (password `partner12345`), riders `+919100000001/2`, customer `+919200000001` — OTP `123456`
- Maps (optional, real roads): `deploy/scripts/prepare-osrm.sh <geofabrik .osm.pbf URL>` then `docker compose --profile maps up -d osrm nominatim`

### Android apps
```bash
cd android
./BUILD.sh :app-customer:assembleDebug :app-rider:assembleDebug :app-partner:assembleDebug   # builds in Docker
# point debug builds at your server instead of the emulator host:
LOCALRUSH_API_BASE_URL=http://<server-ip>:8001/api/v1/ LOCALRUSH_WS_BASE_URL=ws://<server-ip>:8001/ws/ ./BUILD.sh :app-customer:assembleDebug
```
APKs land in `android/app-*/build/outputs/apk/debug/`. Open `android/` in Android Studio for normal development.

### Tests
```bash
docker compose exec api pytest                                   # backend (54 tests)
docker compose exec api ruff check .                             # lint
docker run --rm --network localrush_default -v $PWD/backend/scripts:/s:ro python:3.12-slim python -I /s/smoke_e2e.py   # live E2E
cd android && ./BUILD.sh testDebugUnitTest                       # Android (123 unit tests)
cd android && ./BUILD.sh :app-customer:recordPaparazziDebug      # screenshot renders (no emulator)
```

## Documentation
| Doc | Content |
|---|---|
| [STRUCTURE.md](STRUCTURE.md) | Full directory/module map |
| [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) | Phase status, verification log, limitations |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System diagram, request flows |
| [docs/ER_DIAGRAM.md](docs/ER_DIAGRAM.md) | Database model |
| [docs/API_CONTRACTS.md](docs/API_CONTRACTS.md) · [docs/api/openapi.yml](docs/api/openapi.yml) | API + WebSocket + push contracts |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Production VPS guide, external services (Razorpay, SMS, Firebase, maps, TLS) |
| [docs/TESTING.md](docs/TESTING.md) | Test suites, load test results |
| [docs/SECURITY.md](docs/SECURITY.md) | Security controls, independent review + fixes |
| [docs/PLAYSTORE_RELEASE.md](docs/PLAYSTORE_RELEASE.md) | Signing, AAB, Play Console checklist |
| [docs/DEMO_GUIDE.md](docs/DEMO_GUIDE.md) | 10-minute end-to-end demo on phones |
| [docs/android/DESIGN_BRIEF.md](docs/android/DESIGN_BRIEF.md) | Screen-by-screen design spec |
| [docs/screenshots/](docs/screenshots/) | Dashboard pages, app screens, tracking animation |

## Production checklist
1. Fill `backend/.env` (secret key, DB/Redis passwords, Razorpay keys + webhook secret, SMS provider, `DB_POOL=true`), put the Firebase service account at `secrets/firebase.json`, add `google-services.json` to each app module.
2. Build map data: `deploy/scripts/prepare-osrm.sh` and `prepare-nominatim.sh` with your region's extract.
3. `cd deploy && DOMAIN=api.example.com EMAIL=ops@example.com ./scripts/init-letsencrypt.sh && docker compose -f docker-compose.prod.yml --env-file ../backend/.env up -d --build`
4. Sign and build the apps (`docs/PLAYSTORE_RELEASE.md`), set `LOCALRUSH_API_BASE_URL=https://api.example.com/api/v1/`.

## Credits / licences
Map data © OpenStreetMap contributors (ODbL). Tiles by OpenFreeMap. Routing: OSRM. Geocoding: Nominatim. Map rendering: MapLibre.
